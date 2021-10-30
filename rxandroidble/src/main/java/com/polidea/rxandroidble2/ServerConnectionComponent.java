package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothGattServer;

import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProvider;
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProviderImpl;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueue;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueueImpl;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionImpl;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternal;
import com.polidea.rxandroidble2.internal.server.RxBleServerState;
import com.polidea.rxandroidble2.internal.server.RxBleServerStateImpl;

import bleshadow.dagger.Binds;
import bleshadow.dagger.BindsInstance;
import bleshadow.dagger.Module;
import bleshadow.dagger.Provides;
import bleshadow.dagger.Subcomponent;
import bleshadow.javax.inject.Named;
import io.reactivex.Scheduler;

@ServerConnectionScope
@Subcomponent(modules = {ServerConnectionComponent.ConnectionModule.class})
public interface ServerConnectionComponent {

    String OPERATION_TIMEOUT = "server-operation-timeout";
    String SERVER_DISCONNECTION_ROUTER = "server-disconnection-router";

    @Subcomponent.Builder
    interface Builder {
        ServerConnectionComponent build();

        @BindsInstance
        Builder operationTimeout(Timeout operationTimeout);

        @BindsInstance
        Builder config(ServerConfig config);
    }

    @Module(subcomponents = {ServerTransactionComponent.class})
    abstract class ConnectionModule {
        @Binds
        @ServerConnectionScope
        abstract RxBleServerConnectionInternal bindRxBleServerConnectionInternal(RxBleServerConnectionImpl rxBleServerConnection);

        @Binds
        @ServerConnectionScope
        abstract RxBleServerConnection bindRxBleServerConnection(RxBleServerConnectionImpl connection);

        @Binds
        @ServerConnectionScope
        abstract ServerConnectionOperationsProvider bindServerConnectionOperationsProvider(
                ServerConnectionOperationsProviderImpl provider
        );

        @Binds
        @ServerConnectionScope
        abstract ServerTransactionFactory bindServerTransactionFactory(ServerTransactionFactoryImpl transactionFactory);

        @Binds
        @ServerConnectionScope
        abstract RxBleServerState bindServerState(RxBleServerStateImpl state);

        @Binds
        @ServerConnectionScope
        abstract ServerOperationQueue bindServerOperationQueue(ServerOperationQueueImpl impl);

        @Provides
        @Named(OPERATION_TIMEOUT)
        static TimeoutConfiguration providesOperationTimeoutConf(
                @Named(ClientComponent.NamedSchedulers.TIMEOUT) Scheduler timeoutScheduler,
                Timeout operationTimeout
        ) {
            return new TimeoutConfiguration(operationTimeout.timeout, operationTimeout.timeUnit, timeoutScheduler);
        }

        @Provides
        @ServerConnectionScope
        static BluetoothGattServer provideBluetoothGattServer(
                RxBleServerConnectionImpl connection
        ) {
                return connection.getBluetoothGattServer();
        }



    }

    RxBleServerConnectionInternal serverConnectionInternal();

}
