package com.polidea.rxandroidble2.internal.connection;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.ServerConfig;
import com.polidea.rxandroidble2.ServerScope;
import com.polidea.rxandroidble2.internal.server.BluetoothGattServerProvider;
import com.polidea.rxandroidble2.internal.server.RxBleGattServerCallback;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.functions.Function;

@ServerScope
public class ServerConnectorImpl implements ServerConnector {
    private final RxBleGattServerCallback rxBleGattServerCallback;
    private final BluetoothGattServerProvider gattServerProvider;
    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final Map<BluetoothDevice, RxBleServerConnection> connectionMap = new HashMap<>();

    @Inject
    public ServerConnectorImpl(
            final RxBleGattServerCallback rxBleGattServerCallback,
            final @Named(ServerComponent.SERVER_CONTEXT) Context context,
            final BluetoothGattServerProvider gattServerProvider,
            final BluetoothManager bluetoothManager
    ) {
        this.rxBleGattServerCallback = rxBleGattServerCallback;
        this.context = context;
        this.gattServerProvider = gattServerProvider;
        this.bluetoothManager = bluetoothManager;
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
            server.addService(entry.getValue());
        }
        return true;
    }

    @Override
    public RxBleServerConnection getConnection(BluetoothDevice device) {
        return connectionMap.get(device);
    }

    @Override
    public Observable<RxBleServerConnection> subscribeToConnections(ServerConfig config) {
        if (gattServerProvider.getBluetoothGatt() == null) {
            BluetoothGattServer bluetoothGattServer = bluetoothManager.openGattServer(
                    context,
                    rxBleGattServerCallback.getBluetoothGattServerCallback()
            );
            gattServerProvider.updateBluetoothGatt(bluetoothGattServer);
        }

        initializeServer(config);

        return rxBleGattServerCallback.getOnConnectionStateChange()
                .map(new Function<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>, RxBleServerConnection>() {
                    @Override
                    public RxBleServerConnection apply(
                            Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState> bluetoothDeviceRxBleConnectionStatePair
                    ) throws Exception {
                        RxBleServerConnection connection
                                = rxBleGattServerCallback.getRxBleServerConnection(bluetoothDeviceRxBleConnectionStatePair.first);
                        connectionMap.put(connection.getDevice(), connection);
                        return connection;
                    }
                });
    }

    @Override
    public void closeServer() {
        if (gattServerProvider.getBluetoothGatt() != null) {
            gattServerProvider.getBluetoothGatt().close();
        }
    }
}
