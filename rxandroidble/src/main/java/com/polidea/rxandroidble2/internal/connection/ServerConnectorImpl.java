package com.polidea.rxandroidble2.internal.connection;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Pair;

import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.internal.server.BluetoothGattServerProvider;
import com.polidea.rxandroidble2.internal.server.RxBleGattServerCallback;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnection;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.functions.Function;

public class ServerConnectorImpl implements ServerConnector {
    private final RxBleGattServerCallback rxBleGattServerCallback;
    private final BluetoothGattServerProvider gattServerProvider;
    private final Context context;

    @Inject
    public ServerConnectorImpl(
            final RxBleGattServerCallback rxBleGattServerCallback,
            final @Named(ServerComponent.SERVER_CONTEXT) Context context,
            final BluetoothGattServerProvider gattServerProvider
    ) {
        this.rxBleGattServerCallback = rxBleGattServerCallback;
        this.context = context;
        this.gattServerProvider = gattServerProvider;
    }

    @Override
    public Observable<RxBleServerConnection> subscribeToConnections() {
        return rxBleGattServerCallback.getOnConnectionStateChange()
                .map(new Function<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>, RxBleServerConnection>() {
                    @Override
                    public RxBleServerConnection apply(
                            Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState> bluetoothDeviceRxBleConnectionStatePair
                    ) throws Exception {
                        return rxBleGattServerCallback.getRxBleServerConnection(bluetoothDeviceRxBleConnectionStatePair.first);
                    }
                });
    }

}
