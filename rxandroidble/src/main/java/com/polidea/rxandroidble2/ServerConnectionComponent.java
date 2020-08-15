package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.internal.server.RxBleServerConnection;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionImpl;

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

    @Module
    abstract class ConnectionModule {
        @Binds
        @ServerConnectionScope
        abstract RxBleServerConnection bindRxBleServerConnection(RxBleServerConnectionImpl rxBleServerConnection);

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


    RxBleServerConnection serverConnection();

}
