package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import java.util.UUID;

public interface ServerTransactionFactory {
    ServerResponseTransaction prepareCharacteristicTransaction(
            byte[] value,
            int requestID,
            int offset,
            BluetoothDevice device
    );

    NotificationSetupTransaction prepareNotificationSetupTransaction(
            BluetoothDevice device,
            UUID characteristic
    );
}
