package com.polidea.rxandroidble2.internal.connection;

import com.polidea.rxandroidble2.ClientComponent;
import com.polidea.rxandroidble2.ClientScope;
import com.polidea.rxandroidble2.RxBleServerConnection;
import com.polidea.rxandroidble2.ServerConfig;
import com.polidea.rxandroidble2.ServerConnectionComponent;
import com.polidea.rxandroidble2.Timeout;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternal;

import java.util.concurrent.Callable;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.functions.Action;

@ClientScope
public class ServerConnectorImpl implements ServerConnector {
    private final Scheduler callbackScheduler;
    private final ServerConnectionComponent.Builder connectionComponentBuilder;

    @Inject
    public ServerConnectorImpl(
            final @Named(ClientComponent.NamedSchedulers.BLUETOOTH_CALLBACKS) Scheduler callbackScheduler,
            ServerConnectionComponent.Builder connectionComponentBuilder
            ) {
        this.callbackScheduler = callbackScheduler;
        this.connectionComponentBuilder = connectionComponentBuilder;
    }

    public Single<RxBleServerConnection> createConnection(final Timeout timeout, final ServerConfig config) {
        final ServerConnectionComponent component = connectionComponentBuilder
                .operationTimeout(timeout)
                .config(config)
                .build();

        final RxBleServerConnectionInternal internal = component.serverConnectionInternal();
        return Single.fromCallable(new Callable<RxBleServerConnection>() {
            @Override
            public RxBleServerConnection call() throws Exception {
                return internal.getConnection();
            }
        });
    }

    @Override
    public Single<RxBleServerConnection> subscribeToConnections(final ServerConfig serverConfig) {
        return createConnection(serverConfig.getOperationTimeout(), serverConfig)
                .doOnDispose(new Action() {
                    @Override
                    public void run() throws Exception {
                        RxBleLog.e("gatt server disposed, closing server");
                    }
                })
                .subscribeOn(callbackScheduler)
                .unsubscribeOn(callbackScheduler);
    }
}
