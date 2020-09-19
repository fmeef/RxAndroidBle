package com.polidea.rxandroidble2.internal.connection;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternal;

import io.reactivex.Observable;

public interface ServerConnector {
    Observable<RxBleServerConnectionInternal> subscribeToConnections();
    RxBleServerConnectionInternal getConnection(BluetoothDevice device);
    void closeServer();
}
