package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;

import com.polidea.rxandroidble2.internal.connection.ServerConnectionScope;
import com.polidea.rxandroidble2.internal.operations.server.ServerOperationsProvider;
import com.polidea.rxandroidble2.internal.operations.server.ServerOperationsProviderImpl;
import com.polidea.rxandroidble2.internal.serialization.RxBleThreadFactory;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueue;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueueImpl;
import com.polidea.rxandroidble2.internal.server.BluetoothGattServerProvider;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnection;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionImpl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bleshadow.dagger.Binds;
import bleshadow.dagger.BindsInstance;
import bleshadow.dagger.Module;
import bleshadow.dagger.Provides;
import bleshadow.dagger.Subcomponent;
import bleshadow.javax.inject.Named;
import io.reactivex.Scheduler;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;

@ServerConnectionScope
@Subcomponent(modules = {ServerConnectionComponent.ConnectionModule.class})
public interface ServerConnectionComponent {
    @Subcomponent.Builder
    interface Builder {
        ServerConnectionComponent build();

        @BindsInstance
        Builder bluetoothDevice(BluetoothDevice device);
    }


    class NamedSchedulers {

        public static final String COMPUTATION = "computation_server";
        public static final String BLUETOOTH_INTERACTION = "bluetooth_interaction_server";
        public static final String BLUETOOTH_CALLBACKS = "bluetooth_callbacks_server";
        private NamedSchedulers() {

        }
    }

    class NamedExecutors {

        public static final String BLUETOOTH_INTERACTION = "executor_bluetooth_interaction_server";
        private NamedExecutors() {

        }
    }

    @Module
    abstract class ConnectionModule {
        @Provides
        static BluetoothGattServer provideBluetoothGattServer(BluetoothGattServerProvider bluetoothGattServerProvider) {
            return bluetoothGattServerProvider.getBluetoothGatt();
        }

        @Binds
        @ServerConnectionScope
        abstract RxBleServerConnection bindRxBleServerConnection(RxBleServerConnectionImpl rxBleServerConnection);

        @Binds
        abstract ServerOperationsProvider bindServerOperationsProvider(ServerOperationsProviderImpl serverOperationsProvider);

        @Provides
        @Named(NamedExecutors.BLUETOOTH_INTERACTION)
        @ServerConnectionScope
        static ExecutorService provideBluetoothInteractionExecutorService() {
            return Executors.newSingleThreadExecutor();
        }

        @Provides
        @Named(NamedSchedulers.BLUETOOTH_INTERACTION)
        @ServerConnectionScope
        static Scheduler provideBluetoothInteractionScheduler(@Named(NamedExecutors.BLUETOOTH_INTERACTION) ExecutorService service) {
            return Schedulers.from(service);
        }

        @Provides
        @Named(NamedSchedulers.BLUETOOTH_CALLBACKS)
        @ServerConnectionScope
        static Scheduler provideBluetoothCallbacksScheduler() {
            return RxJavaPlugins.createSingleScheduler(new RxBleThreadFactory());
        }

        @Provides
        static ServerComponent.ServerComponentFinalizer provideFinalizationCloseable(
                @Named(NamedExecutors.BLUETOOTH_INTERACTION) final ExecutorService interactionExecutorService,
                @Named(NamedSchedulers.BLUETOOTH_CALLBACKS) final Scheduler callbacksScheduler
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
        @Named(NamedSchedulers.COMPUTATION)
        static Scheduler provideComputationScheduler() {
            return Schedulers.computation();
        }

        @Binds
        @ServerConnectionScope
        abstract ServerOperationQueue bindServerOperationQueue(ServerOperationQueueImpl operationQueue);


    }


    RxBleServerConnection serverConnection();

}
