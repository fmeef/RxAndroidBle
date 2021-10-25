package com.polidea.rxandroidble2.exceptions;

import android.bluetooth.BluetoothGattCharacteristic;

public class BleGattServerCharacteristicException extends BleGattServerException {

    public final BluetoothGattCharacteristic characteristic;

    public BleGattServerCharacteristicException(
            BluetoothGattCharacteristic characteristic,
            int status,
            BleGattServerOperationType bleGattOperationType
    ) {
        super(status, bleGattOperationType, "characteristic exception");
        this.characteristic = characteristic;
    }
}
