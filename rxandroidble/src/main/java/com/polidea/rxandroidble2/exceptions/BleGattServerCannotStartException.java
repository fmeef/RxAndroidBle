package com.polidea.rxandroidble2.exceptions;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattServer;

/**
 * An exception emitted from {@link com.polidea.rxandroidble2.RxBleConnection} functions when the underlying {@link BluetoothGatt}
 * returns `false` from {@link BluetoothGatt#readRemoteRssi()} or other functions associated with device interaction.
 */
public class BleGattServerCannotStartException extends BleGattServerException {

    @Deprecated
    public BleGattServerCannotStartException(BleGattServerOperationType bleGattOperationType) {
        super(bleGattOperationType, "server cannot start");
    }

    public BleGattServerCannotStartException(BluetoothGattServer gatt,
                                             BleGattServerOperationType bleGattServerOperationType) {
        super(bleGattServerOperationType, "server cannot start");
    }
}
