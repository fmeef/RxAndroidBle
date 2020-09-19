package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;

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
            byte[] value
    );

    CharacteristicIndicationOperation provideCharacteristicIndicationOperation(
            BluetoothGattCharacteristic characteristic,
            byte[] value
    );
}
