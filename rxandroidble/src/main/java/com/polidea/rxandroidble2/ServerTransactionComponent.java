package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import java.util.UUID;

import bleshadow.dagger.Binds;
import bleshadow.dagger.BindsInstance;
import bleshadow.dagger.Module;
import bleshadow.dagger.Subcomponent;

@ServerTransactionScope
@Subcomponent(modules = {ServerTransactionComponent.TransactionModule.class})
public interface ServerTransactionComponent {

    class TransactionConfig {
        public byte[] value;
        public int requestID;
        public int offset;
        public BluetoothDevice device;
    }

    @Subcomponent.Builder
    interface Builder {
        ServerTransactionComponent build();

        @BindsInstance Builder config(TransactionConfig config);
        @BindsInstance Builder device(BluetoothDevice device);
        @BindsInstance Builder characteristic(UUID characteristic);
    }

    @Module
    abstract class TransactionModule {
        @Binds
        @ServerTransactionScope
        abstract ServerResponseTransaction bindServerTransaction(ServerResponseTransactionImpl transaction);

        @Binds
        @ServerTransactionScope
        abstract NotificationSetupTransaction bindNotificationSetupTransaction(NotificationSetupTransactionImpl transaction);
    }

    ServerResponseTransaction getCharacteristicTransaction();

    NotificationSetupTransaction getNotificationSetupTransaction();
}
