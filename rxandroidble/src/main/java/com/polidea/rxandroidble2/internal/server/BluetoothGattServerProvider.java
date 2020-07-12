package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothGattServer;

import androidx.annotation.NonNull;

import com.polidea.rxandroidble2.ServerScope;

import java.util.concurrent.atomic.AtomicReference;

import bleshadow.javax.inject.Inject;

@ServerScope
public class BluetoothGattServerProvider {
    private final AtomicReference<BluetoothGattServer> reference = new AtomicReference<>();

    @Inject
    BluetoothGattServerProvider() {
    }

    /**
     * Provides most recent instance of the BluetoothGatt. Keep in mind that the gatt may not be available, hence null will be returned.
     */
    public BluetoothGattServer getBluetoothGatt() {
        return reference.get();
    }

    /**
     * Updates GATT instance storage if it wasn't initialized previously.
     */
    public void updateBluetoothGatt(@NonNull BluetoothGattServer bluetoothGattServer) {
        reference.compareAndSet(null, bluetoothGattServer);
    }
}
