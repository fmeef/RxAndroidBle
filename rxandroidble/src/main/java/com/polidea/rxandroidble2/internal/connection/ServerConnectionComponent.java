package com.polidea.rxandroidble2.internal.connection;

import com.polidea.rxandroidble2.internal.server.RxBleServerConnection;

import bleshadow.dagger.Subcomponent;

@ConnectionScope
@Subcomponent(modules = {ServerConnectionModule.class})
public interface ServerConnectionComponent {

    @Subcomponent.Builder
    interface Builder {
        ServerConnectionComponent build();
    }

    @ConnectionScope
    RxBleServerConnection rxBleServerConnection();

}
