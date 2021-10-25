package com.polidea.rxandroidble2.exceptions;

import android.bluetooth.BluetoothGattDescriptor;

public class BleGattServerDescriptorException extends BleGattServerException {

    public final BluetoothGattDescriptor descriptor;

    public BleGattServerDescriptorException(
            BluetoothGattDescriptor descriptor,
            int status,
            BleGattServerOperationType bleGattOperationType
    ) {
        super(status, bleGattOperationType, "descriptor exception");
        this.descriptor = descriptor;
    }
}
