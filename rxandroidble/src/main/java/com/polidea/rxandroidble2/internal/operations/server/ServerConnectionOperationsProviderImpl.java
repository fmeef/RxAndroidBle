package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;

import com.polidea.rxandroidble2.ServerComponent;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Scheduler;

public class ServerConnectionOperationsProviderImpl implements ServerConnectionOperationsProvider {

    private final BluetoothGattServer bluetoothGattServer;
    private final Scheduler gattServerScheduler;


    @Inject
    public ServerConnectionOperationsProviderImpl(
            @Named(ServerComponent.NamedSchedulers.BLUETOOTH_INTERACTION) Scheduler gattServerScheduler,
            BluetoothGattServer bluetoothGattServer
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
                bluetoothGattServer,
                device,
                requestID,
                status,
                offset,
                value
        );
    }
}
