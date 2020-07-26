package com.polidea.rxandroidble2.internal.connection;

import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.Timeout;
import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.operations.server.ServerOperationsProvider;
import com.polidea.rxandroidble2.internal.operations.server.ServerOperationsProviderImpl;

import bleshadow.dagger.Binds;
import bleshadow.dagger.Module;
import bleshadow.dagger.Provides;
import bleshadow.javax.inject.Named;
import io.reactivex.Scheduler;

@Module
public abstract class ServerConnectionModule {


    public static final String OPERATION_TIMEOUT = "server-operation-timeout";

    @Provides
    @Named(OPERATION_TIMEOUT)
    static TimeoutConfiguration providesOperationTimeoutConf(
            @Named(ServerComponent.NamedSchedulers.TIMEOUT) Scheduler timeoutScheduler,
            Timeout operationTimeout
    ) {
        return new TimeoutConfiguration(operationTimeout.timeout, operationTimeout.timeUnit, timeoutScheduler);
    }

    @Binds
    abstract ServerConnector bindConnector(ServerConnectorImpl rxBleServerConnector);

    @Binds
    abstract ServerOperationsProvider bindServerOperationsProvider(ServerOperationsProviderImpl serverOperationsProvider);
}
