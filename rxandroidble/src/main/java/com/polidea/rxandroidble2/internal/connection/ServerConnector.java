package com.polidea.rxandroidble2.internal.connection;

import com.polidea.rxandroidble2.RxBleServerConnection;
import com.polidea.rxandroidble2.ServerConfig;

import io.reactivex.Single;

public interface ServerConnector {
    Single<RxBleServerConnection> subscribeToConnections(ServerConfig config);
}
