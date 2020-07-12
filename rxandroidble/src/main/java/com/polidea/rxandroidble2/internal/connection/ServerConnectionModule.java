package com.polidea.rxandroidble2.internal.connection;

import bleshadow.dagger.Binds;
import bleshadow.dagger.Module;

@Module
public abstract class ServerConnectionModule {
    @Binds
    abstract ServerConnector bindConnector(ServerConnectorImpl rxBleServerConnector);
}
