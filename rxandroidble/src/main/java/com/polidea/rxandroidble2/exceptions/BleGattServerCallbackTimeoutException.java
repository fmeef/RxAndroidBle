package com.polidea.rxandroidble2.exceptions;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;

/**
 * This exception is used when a call on a {@link BluetoothGattServer} has returned true (succeeded) but the corresponding
 * {@link android.bluetooth.BluetoothGattServerCallback} callback was not called after a certain time (usually 30 seconds)
 * which is considered a Android OS BLE Stack misbehaviour
 */
public class BleGattServerCallbackTimeoutException extends BleGattServerException {

    public BleGattServerCallbackTimeoutException(BluetoothGattServer gatt,
                                                 BluetoothDevice device,
                                                 BleGattServerOperationType bleGattOperationType) {
        super(gatt, device, bleGattOperationType);
    }
}