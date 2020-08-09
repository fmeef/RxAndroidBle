package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;

import com.polidea.rxandroidble2.ServerConnectionComponent;
import com.polidea.rxandroidble2.internal.server.RxBleGattServerCallback;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.Scheduler;

public class ServerOperationsProviderImpl implements ServerOperationsProvider {
    private final RxBleGattServerCallback gattServerCallback;
    private final BluetoothGattServer bluetoothGattServer;
    private final Scheduler gattServerScheduler;

    @Inject
    ServerOperationsProviderImpl(
            RxBleGattServerCallback rxBleGattServerCallback,
            @Named(ServerConnectionComponent.NamedSchedulers.BLUETOOTH_INTERACTION) Scheduler gattServerScheduler,
            BluetoothGattServer bluetoothGattServer
    ) {
        this.gattServerScheduler = gattServerScheduler;
        this.gattServerCallback = rxBleGattServerCallback;
        this.bluetoothGattServer = bluetoothGattServer;
    }


    public ServerLongWriteOperation provideLongWriteOperation(Observable<byte[]> bytes, BluetoothDevice device) {
        return new ServerLongWriteOperation(
                gattServerScheduler,
                bytes,
                device
        );
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
