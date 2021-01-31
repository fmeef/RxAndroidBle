package com.polidea.rxandroidble2.internal.connection;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.RxBleServerConnection;
import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.ServerConfig;
import com.polidea.rxandroidble2.ServerConnectionComponent;
import com.polidea.rxandroidble2.ServerScope;
import com.polidea.rxandroidble2.Timeout;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternal;
import com.polidea.rxandroidble2.internal.server.ServerConnectionSubscriptionWatcher;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;

@ServerScope
public class ServerConnectorImpl implements ServerConnector {
    private final Scheduler callbackScheduler;
    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final ServerConnectionComponent.Builder connectionComponentBuilder;
    private final ConcurrentHashMap<BluetoothDevice, RxBleServerConnection> localConnectionmap = new ConcurrentHashMap<>();
    private final BluetoothAdapter adapter;
    private final PublishRelay<List<BluetoothDevice>> getDevicesConnected = PublishRelay.create();
    private final Set<String> connectedMacs = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    @Inject
    public ServerConnectorImpl(
            final @Named(ServerComponent.SERVER_CONTEXT) Context context,
            final BluetoothManager bluetoothManager,
            final @Named(ServerComponent.NamedSchedulers.BLUETOOTH_SERVER) Scheduler callbackScheduler,
            ServerConnectionComponent.Builder connectionComponentBuilder,
            BluetoothAdapter adapter
            ) {
        this.context = context;
        this.bluetoothManager = bluetoothManager;
        this.callbackScheduler = callbackScheduler;
        this.connectionComponentBuilder = connectionComponentBuilder;
        this.adapter = adapter;
        adapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                getDevicesConnected.accept(proxy.getConnectedDevices());
            }

            @Override
            public void onServiceDisconnected(int profile) {

            }
        }, BluetoothProfile.GATT_SERVER);
    }

    public Single<RxBleServerConnection> createConnection(
            final BluetoothDevice device,
            final Timeout timeout,
            final ServerConfig config) {
        final ServerConnectionComponent component = connectionComponentBuilder
                .bluetoothDevice(device)
                .operationTimeout(timeout)
                .serverConfig(config)
                .build();

        final Set<ServerConnectionSubscriptionWatcher> subWatchers = component.connectionSubscriptionWatchers();

        final RxBleServerConnectionInternal internal = component.serverConnectionInternal();
        return Single.fromCallable(new Callable<RxBleServerConnection>() {
            @Override
            public RxBleServerConnection call() throws Exception {
                return internal.getConnection();
            }
        })
                .doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        for (ServerConnectionSubscriptionWatcher s : subWatchers) {
                            s.onConnectionSubscribed();
                        }
                    }
                })
                .doFinally(new Action() {
                    @Override
                    public void run() throws Exception {
                        for (ServerConnectionSubscriptionWatcher s : subWatchers) {
                            s.onConnectionUnsubscribed();
                        }
                    }
                });
    }

    @Override
    public RxBleServerConnection getConnection(BluetoothDevice device) {
        return localConnectionmap.get(device);
    }

    @Override
    public Observable<RxBleServerConnection> subscribeToConnections(final ServerConfig serverConfig) {

        return getDevicesConnected
                .flatMap(new Function<List<BluetoothDevice>, ObservableSource<BluetoothDevice>>() {
                    @Override
                    public ObservableSource<BluetoothDevice> apply(@NonNull List<BluetoothDevice> bluetoothDevices) throws Exception {
                        return Observable.fromIterable(bluetoothDevices);
                    }
                })
                .filter(new Predicate<BluetoothDevice>() {
                    @Override
                    public boolean test(@NonNull BluetoothDevice device) throws Exception {
                        return !connectedMacs.contains(device.getAddress());
                    }
                })
                .map(new Function<BluetoothDevice, BluetoothDevice>() {
                    @Override
                    public BluetoothDevice apply(@NonNull BluetoothDevice device) throws Exception {
                        connectedMacs.add(device.getAddress());
                        return device;
                    }
                })
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable error) throws Exception {
                        RxBleLog.e("debug", "disconnect error");
                    }
                })
                .flatMap(new Function<BluetoothDevice, ObservableSource<RxBleServerConnection>>() {
                    @Override
                    public ObservableSource<RxBleServerConnection> apply(
                            BluetoothDevice p
                    ) throws Exception {
                        return createConnection(p, serverConfig.getOperationTimeout(), serverConfig)
                                .toObservable();
                    }
                })
                .doOnDispose(new Action() {
                    @Override
                    public void run() throws Exception {
                        RxBleLog.e("gatt server disposed, closing server");
                    }
                })
                .subscribeOn(callbackScheduler)
                .unsubscribeOn(callbackScheduler);
    }
}
