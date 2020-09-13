package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;

import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.ServerConnectionComponent;
import com.polidea.rxandroidble2.ServerConnectionScope;
import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.server.BluetoothGattServerProvider;
import com.polidea.rxandroidble2.internal.server.RxBleGattServerCallback;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.Scheduler;

@ServerConnectionScope
public class ServerConnectionOperationsProviderImpl implements ServerConnectionOperationsProvider {

    private final BluetoothGattServerProvider bluetoothGattServer;
    private final Scheduler gattServerScheduler;
    private final BluetoothDevice bluetoothDevice;
    private final RxBleGattServerCallback callback;
    private final BluetoothManager bluetoothManager;
    private final TimeoutConfiguration timeoutConfiguration;


    @Inject
    public ServerConnectionOperationsProviderImpl(
            @Named(ServerComponent.NamedSchedulers.BLUETOOTH_CONNECTION) Scheduler gattServerScheduler,
            BluetoothGattServerProvider bluetoothGattServer,
            BluetoothDevice bluetoothDevice,
            RxBleGattServerCallback callback,
            BluetoothManager bluetoothManager,
            @Named(ServerConnectionComponent.OPERATION_TIMEOUT) TimeoutConfiguration timeoutConfiguration
    ) {
        this.gattServerScheduler = gattServerScheduler;
        this.bluetoothGattServer = bluetoothGattServer;
        this.bluetoothDevice = bluetoothDevice;
        this.callback = callback;
        this.bluetoothManager = bluetoothManager;
        this.timeoutConfiguration = timeoutConfiguration;
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
                timeoutConfiguration,
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
                gattServerScheduler,
                bluetoothManager,
                timeoutConfiguration
                );
    }

    @Override
    public CharacteristicNotificationOperation provideCharacteristicNotificationOperation(
            BluetoothGattCharacteristic characteristic,
            Observable<Integer> notificationCompletedObservable
    ) {
        return new CharacteristicNotificationOperation(
                gattServerScheduler,
                bluetoothDevice,
                bluetoothGattServer,
                notificationCompletedObservable,
                characteristic,
                timeoutConfiguration
                );
    }

    @Override
    public CharacteristicIndicationOperation provideCharacteristicIndicationOperation(
            BluetoothGattCharacteristic characteristic,
            Observable<Integer> notificationCompletedObservable
    ) {
        return new CharacteristicIndicationOperation(
                gattServerScheduler,
                bluetoothDevice,
                bluetoothGattServer,
                notificationCompletedObservable,
                characteristic,
                timeoutConfiguration
        );
    }
}
