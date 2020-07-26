package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothGattServer;

import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.internal.connection.ServerConnectionModule;
import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.server.RxBleGattServerCallback;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.Scheduler;

public class ServerOperationsProviderImpl implements ServerOperationsProvider {
    private final RxBleGattServerCallback gattServerCallback;
    private final BluetoothGattServer bluetoothGattServer;
    private final Scheduler gattServerScheduler;
    private final Scheduler timeoutScheduler;
    private final TimeoutConfiguration timeoutConfiguration;

    @Inject
    ServerOperationsProviderImpl(
            RxBleGattServerCallback rxBleGattServerCallback,
            @Named(ServerComponent.NamedSchedulers.BLUETOOTH_INTERACTION) Scheduler gattServerScheduler,
            @Named(ServerComponent.NamedSchedulers.TIMEOUT) Scheduler timeoutScheduler,
            @Named(ServerConnectionModule.OPERATION_TIMEOUT) TimeoutConfiguration timeoutConfiguration,
            BluetoothGattServer bluetoothGattServer
    ) {
        this.timeoutScheduler = timeoutScheduler;
        this.gattServerScheduler = gattServerScheduler;
        this.gattServerCallback = rxBleGattServerCallback;
        this.bluetoothGattServer = bluetoothGattServer;
        this.timeoutConfiguration = timeoutConfiguration;
    }


    public ServerLongWriteOperation provideLongWriteOperation(Observable<byte[]> bytes) {
        return new ServerLongWriteOperation(
                gattServerScheduler,
                bytes);
    }
}
