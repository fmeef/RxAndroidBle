package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;

import androidx.annotation.NonNull;

import com.polidea.rxandroidble2.ServerScope;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import bleshadow.javax.inject.Inject;

@ServerScope
public class BluetoothGattServerProvider {
    private final AtomicReference<BluetoothGattServer> reference = new AtomicReference<>();
    private final ConcurrentHashMap<BluetoothDevice, RxBleServerConnection> connections = new ConcurrentHashMap<>();

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
     * Access a server connection instance.
     */
    public RxBleServerConnection getConnection(final BluetoothDevice device) {
        return connections.get(device);
    }

    public void updateConnection(final BluetoothDevice device, final RxBleServerConnection connection) {
        connections.putIfAbsent(device, connection);
    }

    public synchronized void closeConnection(final BluetoothDevice device) {
        connections.remove(device);
        //TODO: cleanup connection
    }

    /**
     * Updates GATT instance storage if it wasn't initialized previously.
     */
    public void updateBluetoothGatt(@NonNull BluetoothGattServer bluetoothGattServer) {
        reference.compareAndSet(null, bluetoothGattServer);
    }
}
