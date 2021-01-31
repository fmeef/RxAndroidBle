package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothGattServer;

import com.polidea.rxandroidble2.ServerConnectionScope;

import java.util.concurrent.atomic.AtomicReference;

import bleshadow.javax.inject.Inject;

@ServerConnectionScope
public class BluetoothGattServerProvider {
    private final AtomicReference<BluetoothGattServer> serverReference = new AtomicReference<>();
    private final AtomicReference<RxBleServerConnectionInternal> connection = new AtomicReference<>();
    @Inject
    BluetoothGattServerProvider() {
    }

    public BluetoothGattServer getServer() {
        return serverReference.get();
    }

    public void updateServer(BluetoothGattServer server) {
        serverReference.set(server);
    }


    public RxBleServerConnectionInternal getConnection() {
        return connection.get();
    }

    public void updateConnection(RxBleServerConnectionInternal connectionInternal) {
        this.connection.set(connectionInternal);
    }
}
