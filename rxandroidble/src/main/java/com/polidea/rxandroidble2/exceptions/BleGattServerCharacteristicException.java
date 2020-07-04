package com.polidea.rxandroidble2.exceptions;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;

public class BleGattServerCharacteristicException extends BleGattServerException {

    public final BluetoothGattCharacteristic characteristic;

    public BleGattServerCharacteristicException(
            BluetoothGattCharacteristic characteristic,
            BluetoothDevice device,
            int status,
            BleGattServerOperationType bleGattOperationType
    ) {
        super(status, device, bleGattOperationType);
        this.characteristic = characteristic;
    }
}
