package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.internal.connection.ConnectionSubscriptionWatcher;
import com.polidea.rxandroidble2.internal.connection.DisconnectionRouterOutput;
import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProvider;
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProviderImpl;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueue;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueueImpl;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternal;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternalImpl;
import com.polidea.rxandroidble2.internal.server.ServerDisconnectionRouter;

import java.util.Set;
import java.util.concurrent.ExecutorService;

import bleshadow.dagger.Binds;
import bleshadow.dagger.BindsInstance;
import bleshadow.dagger.Module;
import bleshadow.dagger.Provides;
import bleshadow.dagger.Subcomponent;
import bleshadow.dagger.multibindings.IntoSet;
import bleshadow.javax.inject.Named;
import io.reactivex.Scheduler;

@ServerConnectionScope
@Subcomponent(modules = {ServerConnectionComponent.ConnectionModule.class})
public interface ServerConnectionComponent {

    String OPERATION_TIMEOUT = "server-operation-timeout";
    String SERVER_DISCONNECTION_ROUTER = "server-disconnection-router";
    String SERVER_CONNECTION_SUBSCRIPTION_WATCHER = "connection-subscription-watcher";

    @Subcomponent.Builder
    interface Builder {
        ServerConnectionComponent build();

        @BindsInstance
        Builder operationTimeout(Timeout operationTimeout);

        @BindsInstance
        Builder bluetoothDevice(BluetoothDevice device);
    }

    @Module(subcomponents = {ServerTransactionComponent.class})
    abstract class ConnectionModule {
        @Binds
        abstract RxBleServerConnectionInternal bindRxBleServerConnectionInternal(RxBleServerConnectionInternalImpl rxBleServerConnection);

        @Binds
        @ServerConnectionScope
        abstract RxBleServerConnection bindRxBleServerConnection(RxBleServerConnectionInternalImpl connection);

        @Binds
        abstract ServerConnectionOperationsProvider bindServerConnectionOperationsProvider(
                ServerConnectionOperationsProviderImpl provider
        );

        @Binds
        abstract ServerTransactionFactory bindServerTransactionFactory(ServerTransactionFactoryImpl transactionFactory);

        @Binds
        @IntoSet
        abstract ConnectionSubscriptionWatcher bindSubscriptionWatcher(ServerOperationQueueImpl q);

        @Binds
        @ServerConnectionScope
        @Named(SERVER_DISCONNECTION_ROUTER)
        abstract DisconnectionRouterOutput bindDisconnectionRouterOutput(ServerDisconnectionRouter disconnectionRouter);


        @Binds
        @ServerConnectionScope
        abstract ServerOperationQueue bindServerOperationQueue(ServerOperationQueueImpl impl);

        @Provides
        static ServerComponent.ServerComponentFinalizer provideFinalizationCloseable(
                @Named(ServerComponent.NamedExecutors.BLUETOOTH_INTERACTION) final ExecutorService interactionExecutorService,
                @Named(ServerComponent.NamedSchedulers.BLUETOOTH_CONNECTION) final Scheduler callbacksScheduler
        ) {
            return new ServerComponent.ServerComponentFinalizer() {
                @Override
                public void onFinalize() {
                    interactionExecutorService.shutdown();
                    callbacksScheduler.shutdown();
                }
            };
        }

        @Provides
        @Named(OPERATION_TIMEOUT)
        static TimeoutConfiguration providesOperationTimeoutConf(
                @Named(ServerComponent.NamedSchedulers.TIMEOUT) Scheduler timeoutScheduler,
                Timeout operationTimeout
        ) {
            return new TimeoutConfiguration(operationTimeout.timeout, operationTimeout.timeUnit, timeoutScheduler);
        }



    }

    Set<ConnectionSubscriptionWatcher> connectionSubscriptionWatchers();

    RxBleServerConnectionInternal serverConnectionInternal();

}
