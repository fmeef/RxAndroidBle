package com.polidea.rxandroidble2;


import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

import java.util.concurrent.Callable;

import bleshadow.javax.inject.Inject;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.functions.Function;

public class ServerTransactionFactoryImpl implements ServerTransactionFactory {
    final ServerTransactionComponent.Builder transactionComponentBuilder;

    @Inject
    public ServerTransactionFactoryImpl(
            ServerTransactionComponent.Builder transactionComponentBuilder
    ) {
        this.transactionComponentBuilder = transactionComponentBuilder;
    }


    @Override
    public Single<ServerResponseTransaction> prepareCharacteristicTransaction(
            final byte[] value,
            final int requestID,
            final int offset,
            final BluetoothDevice device
    ) {
        return Single.defer(new Callable<SingleSource<? extends ServerResponseTransaction>>() {
            @Override
            public SingleSource<? extends ServerResponseTransaction> call() throws Exception {
                return Single.fromCallable(new Callable<ServerResponseTransaction>() {
                    @Override
                    public ServerResponseTransaction call() throws Exception {
                        final ServerTransactionComponent.TransactionConfig config = new ServerTransactionComponent.TransactionConfig();
                        config.device = device;
                        config.offset = offset;
                        config.requestID = requestID;
                        config.value = value;
                        final ServerTransactionComponent transactionComponent = transactionComponentBuilder
                                .config(config)
                                .build();
                        return transactionComponent.getCharacteristicTransaction();
                    }
                });
            }
        });
    }

    @Override
    public Single<NotificationSetupTransaction> prepareNotificationSetupTransaction(BluetoothDevice device) {

        return Single.just(device)
                .map(new Function<BluetoothDevice, NotificationSetupTransaction>() {
                    @Override
                    public NotificationSetupTransaction apply(@NonNull BluetoothDevice device) throws Exception {
                        final ServerTransactionComponent.TransactionConfig config = new ServerTransactionComponent.TransactionConfig();
                        config.device = device;
                        final ServerTransactionComponent transactionComponent = transactionComponentBuilder
                                .config(config)
                                .build();
                        return transactionComponent.getNotificationSetupTransaction();
                    }
                });
    }
}
