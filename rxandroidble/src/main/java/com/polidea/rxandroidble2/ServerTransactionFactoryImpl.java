package com.polidea.rxandroidble2;


import java.util.concurrent.Callable;

import bleshadow.javax.inject.Inject;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;

public class ServerTransactionFactoryImpl {
    final ServerTransactionComponent.Builder transactionComponentBuilder;

    @Inject
    public ServerTransactionFactoryImpl(
            ServerTransactionComponent.Builder transactionComponentBuilder
    ) {
        this.transactionComponentBuilder = transactionComponentBuilder;
    }


    public Observable<ServerResponseTransaction> prepareCharacteristicTransaction() {
        return Observable.defer(new Callable<ObservableSource<? extends ServerResponseTransaction>>() {
            @Override
            public ObservableSource<? extends ServerResponseTransaction> call() throws Exception {
                final ServerTransactionComponent transactionComponent = transactionComponentBuilder.build();
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
