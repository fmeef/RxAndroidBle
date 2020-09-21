package com.polidea.rxandroidble2.internal.connection;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.RxBleServerConnection;

import io.reactivex.Observable;

public interface ServerConnector {
    Observable<RxBleServerConnection> subscribeToConnections();
    RxBleServerConnection getConnection(BluetoothDevice device);
    void closeServer();
}
