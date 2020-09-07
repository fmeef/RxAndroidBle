package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.internal.connection.DisconnectionRouterOutput;
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProvider;
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProviderImpl;
import com.polidea.rxandroidble2.internal.serialization.ServerConnectionOperationQueue;
import com.polidea.rxandroidble2.internal.serialization.ServerConnectionOperationQueueImpl;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnection;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionImpl;
import com.polidea.rxandroidble2.internal.server.ServerDisconnectionRouter;

import java.util.concurrent.ExecutorService;

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
    @Subcomponent.Builder
    interface Builder {
        ServerConnectionComponent build();

        @BindsInstance
        Builder bluetoothDevice(BluetoothDevice device);
    }

    @Module(subcomponents = {ServerTransactionComponent.class})
    abstract class ConnectionModule {
        @Binds
        @ServerConnectionScope
        abstract RxBleServerConnection bindRxBleServerConnection(RxBleServerConnectionImpl rxBleServerConnection);

        @Binds
        abstract ServerConnectionOperationQueue bindServerConnectionOperationQueue(ServerConnectionOperationQueueImpl queue);

        @Binds
        abstract ServerConnectionOperationsProvider bindServerConnectionOperationsProvider(
                ServerConnectionOperationsProviderImpl provider
        );

        @Binds
        abstract ServerTransactionFactory bindServerTransactionFactory(ServerTransactionFactoryImpl transactionFactory);

        @Binds
        abstract DisconnectionRouterOutput bindDisconnectionRouterOutput(ServerDisconnectionRouter disconnectionRouter);

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
    }

    @ServerConnectionScope
    RxBleServerConnection serverConnection();

}
