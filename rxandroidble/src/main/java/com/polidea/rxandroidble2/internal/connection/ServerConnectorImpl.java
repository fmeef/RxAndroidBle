package com.polidea.rxandroidble2.internal.connection;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;
import android.content.Context;
import android.util.Pair;

import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.internal.server.RxBleGattServerCallback;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnection;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.functions.Function;

public class ServerConnectorImpl implements ServerConnector {
    private final RxBleGattServerCallback rxBleGattServerCallback;
    private final BluetoothGattProvider<BluetoothGattServer> bluetoothGattProvider;
    private final ServerConnectionComponent.Builder connectionComponentBuilder;
    private final Context context;

    @Inject
    public ServerConnectorImpl(
            final RxBleGattServerCallback rxBleGattServerCallback,
            final BluetoothGattProvider<BluetoothGattServer> bluetoothGattProvider,
            final ServerConnectionComponent.Builder connectionComponentBuilder,
            final @Named(ServerComponent.SERVER_CONTEXT) Context context

    ) {
        this.rxBleGattServerCallback = rxBleGattServerCallback;
        this.bluetoothGattProvider = bluetoothGattProvider;
        this.connectionComponentBuilder = connectionComponentBuilder;
        this.context = context;
    }

    @Override
    public Observable<RxBleServerConnection> subscribeToConnections() {
        return rxBleGattServerCallback.getOnConnectionStateChange()
                .map(new Function<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>, RxBleServerConnection>() {
                    @Override
                    public RxBleServerConnection apply(
                            Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState> bluetoothDeviceRxBleConnectionStatePair
                    ) throws Exception {
                        return null; //TODO:
                    }
                });
    }


}
