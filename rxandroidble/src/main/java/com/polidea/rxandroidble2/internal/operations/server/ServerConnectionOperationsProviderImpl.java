package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.internal.server.BluetoothGattServerProvider;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Scheduler;

public class ServerConnectionOperationsProviderImpl implements ServerConnectionOperationsProvider {

    private final BluetoothGattServerProvider bluetoothGattServer;
    private final Scheduler gattServerScheduler;


    @Inject
    public ServerConnectionOperationsProviderImpl(
            @Named(ServerComponent.NamedSchedulers.BLUETOOTH_INTERACTION) Scheduler gattServerScheduler,
            BluetoothGattServerProvider bluetoothGattServer
    ) {
        this.gattServerScheduler = gattServerScheduler;
        this.bluetoothGattServer = bluetoothGattServer;
    }




    public ServerReplyOperation provideReplyOperation(
            BluetoothDevice device,
            int requestID,
            int status,
            int offset,
            byte[] value
    ) {
        return new ServerReplyOperation(
                gattServerScheduler,
                bluetoothGattServer.getBluetoothGatt(),
                device,
                requestID,
                status,
                offset,
                value
        );
    }
}
