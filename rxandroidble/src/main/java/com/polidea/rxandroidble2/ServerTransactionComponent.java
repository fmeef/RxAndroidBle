package com.polidea.rxandroidble2;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

import bleshadow.dagger.Binds;
import bleshadow.dagger.BindsInstance;
import bleshadow.dagger.Module;
import bleshadow.dagger.Subcomponent;

@ServerTransactionScope
@Subcomponent(modules = {ServerTransactionComponent.TransactionModule.class})
public interface ServerTransactionComponent {

    class TransactionConfig {
        @Nullable public byte[] value;
        @NonNull public int requestID;
        @NonNull public int offset;
    }

    @Subcomponent.Builder
    interface Builder {
        ServerTransactionComponent build();

        @BindsInstance Builder config(TransactionConfig config);
        @BindsInstance Builder device(RxBleDevice device);
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
