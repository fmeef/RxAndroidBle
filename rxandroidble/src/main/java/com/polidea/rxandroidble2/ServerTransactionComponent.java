package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.internal.connection.ServerConnectionScope;

import bleshadow.dagger.Binds;
import bleshadow.dagger.BindsInstance;
import bleshadow.dagger.Module;
import bleshadow.dagger.Subcomponent;
import bleshadow.javax.inject.Named;

@ServerConnectionScope
@Subcomponent(modules = {ServerTransactionComponent.TransactionModule.class})
public interface ServerTransactionComponent {

    class TransactionParameters {
        private TransactionParameters() {
        }
        public static final String PARAM_REQUESTID = "requestID";
        public static final String PARAM_OFFSET = "offset";
    }

    @Subcomponent.Builder
    interface Builder {
        ServerTransactionComponent build();

        @BindsInstance
        Builder setValue(byte[] value);

        @BindsInstance
        @Named(TransactionParameters.PARAM_REQUESTID)
        Builder setRequestId(Integer requestId);

        @BindsInstance
        @Named(TransactionParameters.PARAM_OFFSET)
        Builder setOffset(Integer offset);

        @BindsInstance
        Builder setRemoteDevice(BluetoothDevice device);
    }

    @Module
    abstract class TransactionModule {
        @Binds
        @ServerConnectionScope
        abstract ServerResponseTransaction bindServerTransaction(ServerResponseTransactionImpl transaction);
    }

    ServerResponseTransaction getCharacteristicTransaction();
}
