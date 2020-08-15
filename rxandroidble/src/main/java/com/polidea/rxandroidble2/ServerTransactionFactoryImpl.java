package com.polidea.rxandroidble2;


import android.bluetooth.BluetoothDevice;

import java.util.concurrent.Callable;

import bleshadow.javax.inject.Inject;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;

public class ServerTransactionFactoryImpl implements ServerTransactionFactory {
    final ServerTransactionComponent.Builder transactionComponentBuilder;

    @Inject
    public ServerTransactionFactoryImpl(
            ServerTransactionComponent.Builder transactionComponentBuilder
    ) {
        this.transactionComponentBuilder = transactionComponentBuilder;
    }


    @Override
    public Observable<ServerResponseTransaction> prepareCharacteristicTransaction(
            final byte[] value,
            final int requestID,
            final int offset,
            final BluetoothDevice device
    ) {
        return Observable.defer(new Callable<ObservableSource<? extends ServerResponseTransaction>>() {
            @Override
            public ObservableSource<? extends ServerResponseTransaction> call() throws Exception {
                final ServerTransactionComponent.TransactionConfig config = new ServerTransactionComponent.TransactionConfig();
                config.device = device;
                config.offset = offset;
                config.requestID = requestID;
                config.value = value;
                final ServerTransactionComponent transactionComponent = transactionComponentBuilder
                        .config(config)
                        .build();
                return Observable.fromCallable(new Callable<ServerResponseTransaction>() {
                    @Override
                    public ServerResponseTransaction call() throws Exception {
                        return transactionComponent.getCharacteristicTransaction();
                    }
                });
            }
        });
    }
}
