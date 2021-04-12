package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.RestrictTo;

import com.polidea.rxandroidble2.internal.connection.DisconnectionRouterOutput;
import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProvider;
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProviderImpl;
import com.polidea.rxandroidble2.internal.serialization.RxBleThreadFactory;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueue;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueueImpl;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternal;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternalImpl;
import com.polidea.rxandroidble2.internal.server.ServerDisconnectionRouter;

import bleshadow.dagger.Binds;
import bleshadow.dagger.BindsInstance;
import bleshadow.dagger.Module;
import bleshadow.dagger.Provides;
import bleshadow.dagger.Subcomponent;
import bleshadow.javax.inject.Named;
import io.reactivex.Scheduler;
import io.reactivex.plugins.RxJavaPlugins;

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
        @ServerConnectionScope
        abstract RxBleServerConnectionInternal bindRxBleServerConnectionInternal(RxBleServerConnectionInternalImpl rxBleServerConnection);

        @Binds
        @ServerConnectionScope
        abstract RxBleServerConnection bindRxBleServerConnection(RxBleServerConnectionInternalImpl connection);

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
        @Named(SERVER_DISCONNECTION_ROUTER)
        abstract DisconnectionRouterOutput bindDisconnectionRouterOutput(ServerDisconnectionRouter disconnectionRouter);


        @Binds
        @ServerConnectionScope
        abstract ServerOperationQueue bindServerOperationQueue(ServerOperationQueueImpl impl);

        @Provides
        @Named(ServerComponent.NamedSchedulers.BLUETOOTH_CONNECTION)
        @ServerConnectionScope
        static Scheduler provideBluetoothConnectionScheduler() {
            return RxJavaPlugins.createSingleScheduler(new RxBleThreadFactory());
        }

        @Provides
        @ServerConnectionScope
        static ServerConnectionComponent.ServerConnectionComponentFinalizer finalizer(
                @Named(ServerComponent.NamedSchedulers.BLUETOOTH_CONNECTION) final Scheduler scheduler
                ) {
            return new ServerConnectionComponentFinalizer() {
                @Override
                public void onFinalize() {
                    scheduler.shutdown();
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

    RxBleServerConnectionInternal serverConnectionInternal();

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    interface ServerConnectionComponentFinalizer {

        void onFinalize();
    }

}
