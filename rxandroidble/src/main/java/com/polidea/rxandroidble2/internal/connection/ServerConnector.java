package com.polidea.rxandroidble2.internal.connection;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.RxBleServerConnection;
import com.polidea.rxandroidble2.ServerConfig;

import io.reactivex.Observable;

public interface ServerConnector {
    Observable<RxBleServerConnection> subscribeToConnections(ServerConfig config);
    RxBleServerConnection getConnection(BluetoothDevice device);
    void closeServer();
}
