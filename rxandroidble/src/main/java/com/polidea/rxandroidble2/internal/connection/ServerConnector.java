package com.polidea.rxandroidble2.internal.connection;

import com.polidea.rxandroidble2.ServerConfig;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnection;

import io.reactivex.Observable;

public interface ServerConnector {
    Observable<RxBleServerConnection> subscribeToConnections(ServerConfig config);
    void closeServer();
}
