package com.polidea.rxandroidble2.internal.connection;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleServer;
import com.polidea.rxandroidble2.RxBleServerConnection;
import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.ServerConfig;
import com.polidea.rxandroidble2.ServerConnectionComponent;
import com.polidea.rxandroidble2.ServerScope;
import com.polidea.rxandroidble2.Timeout;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.server.BluetoothGattServerProvider;
import com.polidea.rxandroidble2.internal.server.RxBleGattServerCallback;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternal;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;

@ServerScope
public class ServerConnectorImpl implements ServerConnector {
    private final BluetoothGattServerProvider gattServerProvider;
    private final Scheduler callbackScheduler;
    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final ServerConnectionComponent.Builder connectionComponentBuilder;
    private final RxBleGattServerCallback rxBleGattServerCallback;
    private final ServerConfig serverConfig;
    private final ConcurrentHashMap<BluetoothDevice, RxBleServerConnection> localConnectionmap = new ConcurrentHashMap<>();

    @Inject
    public ServerConnectorImpl(
            final @Named(ServerComponent.SERVER_CONTEXT) Context context,
            final BluetoothGattServerProvider gattServerProvider,
            final BluetoothManager bluetoothManager,
            final @Named(ServerComponent.NamedSchedulers.BLUETOOTH_SERVER) Scheduler callbackScheduler,
            ServerConnectionComponent.Builder connectionComponentBuilder,
            RxBleGattServerCallback rxBleGattServerCallback,
            ServerConfig serverConfig
            ) {
        this.context = context;
        this.gattServerProvider = gattServerProvider;
        this.bluetoothManager = bluetoothManager;
        this.callbackScheduler = callbackScheduler;
        this.connectionComponentBuilder = connectionComponentBuilder;
        this.rxBleGattServerCallback = rxBleGattServerCallback;
        this.serverConfig = serverConfig;
    }

    private boolean initializeServer(ServerConfig config) {
        BluetoothGattServer server = gattServerProvider.getBluetoothGatt();
        if (server == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int phyval = 0;
            for (ServerConfig.BluetoothPhy phy : config.getPhySet()) {
                switch (phy) {
                    case PHY_LE_1M:
                        phyval |= BluetoothDevice.PHY_LE_1M_MASK;
                        break;
                    case PHY_LE_2M:
                        phyval |= BluetoothDevice.PHY_LE_2M_MASK;
                        break;
                    case PHY_LE_CODED:
                        phyval |= BluetoothDevice.PHY_LE_CODED_MASK;
                        break;
                    default:
                        // here to please linter
                        Log.e("debug", "we should never reach here");
                        break;
                }
            }
        }

        for (Map.Entry<UUID, BluetoothGattService> entry : config.getServices().entrySet()) {
            for (BluetoothGattCharacteristic characteristic : entry.getValue().getCharacteristics()) {
                if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0
                || (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0) {
                    characteristic.addDescriptor(new BluetoothGattDescriptor(
                            RxBleServer.CLIENT_CONFIG,
                            BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ
                    ));
                }
            }
            server.addService(entry.getValue());
        }

        return true;
    }

    public Single<RxBleServerConnection> createConnection(final BluetoothDevice device, final Timeout timeout) {
        return Single.fromCallable(new Callable<RxBleServerConnection>() {
            @Override
            public RxBleServerConnection call() throws Exception {
                final ServerConnectionComponent component = connectionComponentBuilder
                        .bluetoothDevice(device)
                        .operationTimeout(timeout)
                        .build();

                RxBleServerConnectionInternal internal = component.serverConnectionInternal();
                gattServerProvider.updateConnection(device, internal);
                return internal.getConnection();
            }
        });
    }

    @Override
    public RxBleServerConnection getConnection(BluetoothDevice device) {
        return localConnectionmap.get(device);
    }

    @Override
    public Observable<RxBleServerConnection> subscribeToConnections() {
        if (gattServerProvider.getBluetoothGatt() == null) {
            BluetoothGattServer bluetoothGattServer = bluetoothManager.openGattServer(
                    context,
                    rxBleGattServerCallback.getBluetoothGattServerCallback()
            );
            gattServerProvider.updateBluetoothGatt(bluetoothGattServer);
            initializeServer(serverConfig);
        }

        return rxBleGattServerCallback.getOnConnectionStateChange()
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable error) throws Exception {
                        RxBleLog.e("debug", "disconnect error");
                    }
                })
                .filter(new Predicate<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>>() {
                    @Override
                    public boolean test(
                            Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState> bluetoothDeviceRxBleConnectionStatePair
                    ) throws Exception {
                        if (bluetoothDeviceRxBleConnectionStatePair.second == RxBleConnection.RxBleConnectionState.CONNECTED
                                || bluetoothDeviceRxBleConnectionStatePair.second == RxBleConnection.RxBleConnectionState.CONNECTING)  {
                            return true;
                        } else {
                            gattServerProvider.closeConnection(bluetoothDeviceRxBleConnectionStatePair.first);
                            return false;
                        }
                    }
                })
                .flatMap(
                        new Function<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>,
                                ObservableSource<RxBleServerConnection>>() {
                    @Override
                    public ObservableSource<RxBleServerConnection> apply(
                            Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState> p
                    ) throws Exception {
                        return createConnection(p.first, serverConfig.getOperationTimeout())
                                .toObservable();
                    }
                })
                .map(new Function<RxBleServerConnection, RxBleServerConnection>() {
                    @SuppressLint("CheckResult")
                    @Override
                    public RxBleServerConnection apply(final RxBleServerConnection connection) throws Exception {
                        connection.observeDisconnect().doOnError(new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) throws Exception {

                            }
                        });
                        return connection;
                    }
                })
                .doOnDispose(new Action() {
                    @Override
                    public void run() throws Exception {
                        closeServer();
                    }
                })
                .subscribeOn(callbackScheduler)
                .unsubscribeOn(callbackScheduler);
    }

    @Override
    public void closeServer() {
        if (gattServerProvider.getBluetoothGatt() != null) {
            gattServerProvider.getBluetoothGatt().close();
        }
    }
}
