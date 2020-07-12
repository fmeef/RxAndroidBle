package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothGattServer;

import com.polidea.rxandroidble2.internal.connection.ServerConnectionScope;
import com.polidea.rxandroidble2.internal.server.BluetoothGattServerProvider;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnection;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionImpl;

import bleshadow.dagger.Binds;
import bleshadow.dagger.Module;
import bleshadow.dagger.Provides;
import bleshadow.dagger.Subcomponent;

@ServerConnectionScope
@Subcomponent(modules = {ServerConnectionComponent.ConnectionModule.class})
public interface ServerConnectionComponent {
    @Subcomponent.Builder
    interface Builder {
        ServerConnectionComponent build();
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
    }


    RxBleServerConnection serverConnection();

}
