package com.polidea.rxandroidble2.exceptions;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattDescriptor;

public class BleGattServerDescriptorException extends BleGattServerException {

    public final BluetoothGattDescriptor descriptor;

    public BleGattServerDescriptorException(
            BluetoothGattDescriptor descriptor,
            BluetoothDevice device,
            int status,
            BleGattServerOperationType bleGattOperationType
    ) {
        super(status, device, bleGattOperationType);
        this.descriptor = descriptor;
    }
}
