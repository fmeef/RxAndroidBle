package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;

import androidx.annotation.NonNull;

import com.polidea.rxandroidble2.ClientScope;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import bleshadow.javax.inject.Inject;

@ClientScope
public class BluetoothGattServerProvider {
    private final AtomicReference<BluetoothGattServer> reference = new AtomicReference<>();
    private final ConcurrentHashMap<BluetoothDevice, RxBleServerConnectionInternal> connections = new ConcurrentHashMap<>();

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
    public RxBleServerConnectionInternal getConnection(final BluetoothDevice device) {
        return connections.get(device);
    }

    public void updateConnection(final BluetoothDevice device, final RxBleServerConnectionInternal connection) {
        connections.putIfAbsent(device, connection);
    }

    public synchronized void closeConnection(final BluetoothDevice device) {
        RxBleServerConnectionInternal c = connections.get(device);
        if (c != null) {
            c.dispose();
        }
        connections.remove(device);
        //TODO: cleanup connection
    }

    public int getConnectionCount() {
        return connections.size();
    }

    /**
     * Updates GATT instance storage if it wasn't initialized previously.
     */
    public void updateBluetoothGatt(@NonNull BluetoothGattServer bluetoothGattServer) {
        reference.compareAndSet(null, bluetoothGattServer);
    }
}
