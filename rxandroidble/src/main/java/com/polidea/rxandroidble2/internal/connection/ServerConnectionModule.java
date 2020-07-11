package com.polidea.rxandroidble2.internal.connection;

import android.bluetooth.BluetoothGattServer;

import bleshadow.dagger.Binds;
import bleshadow.dagger.Provides;
import bleshadow.dagger.Module;

@Module
public abstract class ServerConnectionModule {
    @Provides
    static BluetoothGattServer proviedesBluetoothGattServer(BluetoothGattProvider<BluetoothGattServer> bluetoothGattProvider) {
        return bluetoothGattProvider.getBluetoothGatt();
    }

    @Binds
    abstract ServerConnector bindConnector(ServerConnectorImpl rxBleServerConnector);
}
