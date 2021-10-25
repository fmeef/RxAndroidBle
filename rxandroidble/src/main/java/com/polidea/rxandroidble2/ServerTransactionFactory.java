package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import java.util.UUID;

import io.reactivex.Single;

public interface ServerTransactionFactory {
    Single<ServerResponseTransaction> prepareCharacteristicTransaction(
            byte[] value,
            int requestID,
            int offset,
            BluetoothDevice device
    );

    Single<NotificationSetupTransaction> prepareNotificationSetupTransaction(
            BluetoothDevice device,
            UUID characteristic
    );
}
