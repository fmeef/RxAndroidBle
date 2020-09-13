package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;

import io.reactivex.Observable;

public interface ServerConnectionOperationsProvider {
    ServerReplyOperation provideReplyOperation(
            BluetoothDevice device,
            int requestID,
            int status,
            int offset,
            byte[] value
    );

    ServerDisconnectOperation provideDisconnectOperation(BluetoothDevice device);

    CharacteristicNotificationOperation provideCharacteristicNotificationOperation(
            BluetoothGattCharacteristic characteristic,
            Observable<Integer> notificationCompletedObservable
    );

    CharacteristicIndicationOperation provideCharacteristicIndicationOperation(
            BluetoothGattCharacteristic characteristic,
            Observable<Integer> notificationCompletedObservable
    );
}
