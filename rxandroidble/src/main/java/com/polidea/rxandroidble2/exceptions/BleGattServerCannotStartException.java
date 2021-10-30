package com.polidea.rxandroidble2.exceptions;

import android.bluetooth.BluetoothGatt;

/**
 * An exception emitted from {@link com.polidea.rxandroidble2.RxBleConnection} functions when the underlying {@link BluetoothGatt}
 * returns `false` from {@link BluetoothGatt#readRemoteRssi()} or other functions associated with device interaction.
 */
public class BleGattServerCannotStartException extends BleGattServerException {

    @Deprecated
    public BleGattServerCannotStartException(BleGattServerOperationType bleGattOperationType) {
        super(bleGattOperationType, "server cannot start");
    }

}
