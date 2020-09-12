package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.ServerConnectionScope;
import com.polidea.rxandroidble2.internal.server.BluetoothGattServerProvider;
import com.polidea.rxandroidble2.internal.server.RxBleGattServerCallback;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Scheduler;

@ServerConnectionScope
public class ServerConnectionOperationsProviderImpl implements ServerConnectionOperationsProvider {

    private final BluetoothGattServerProvider bluetoothGattServer;
    private final Scheduler gattServerScheduler;
    private final BluetoothDevice bluetoothDevice;
    private final RxBleGattServerCallback callback;


    @Inject
    public ServerConnectionOperationsProviderImpl(
            @Named(ServerComponent.NamedSchedulers.BLUETOOTH_INTERACTION) Scheduler gattServerScheduler,
            BluetoothGattServerProvider bluetoothGattServer,
            BluetoothDevice bluetoothDevice,
            RxBleGattServerCallback callback
    ) {
        this.gattServerScheduler = gattServerScheduler;
        this.bluetoothGattServer = bluetoothGattServer;
        this.bluetoothDevice = bluetoothDevice;
        this.callback = callback;
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

    public ServerDisconnectOperation provideDisconnectOperation(BluetoothDevice device) {
        return new ServerDisconnectOperation(
                bluetoothGattServer,
                device,
                callback,
                gattServerScheduler
                );
    }
}
