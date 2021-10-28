package com.polidea.rxandroidble2.internal.connection;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicReference;

import bleshadow.javax.inject.Inject;

@ConnectionScope
public class BluetoothGattProvider<T> {

    private final AtomicReference<T> reference = new AtomicReference<>();

    @Inject
    BluetoothGattProvider() {
    }

    /**
     * Provides most recent instance of the BluetoothGatt. Keep in mind that the gatt may not be available, hence null will be returned.
     */
    public T getBluetoothGatt() {
        return reference.get();
    }

    /**
     * Updates GATT instance storage if it wasn't initialized previously.
     */
    public void updateBluetoothGatt(@NonNull T bluetoothGatt) {
        reference.compareAndSet(null, bluetoothGatt);
    }
}
