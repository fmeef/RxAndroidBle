package com.polidea.rxandroidble2;


import android.bluetooth.BluetoothDevice;

import java.util.UUID;

import bleshadow.javax.inject.Inject;

public class ServerTransactionFactoryImpl implements ServerTransactionFactory {
    final ServerTransactionComponent.Builder transactionComponentBuilder;

    @Inject
    public ServerTransactionFactoryImpl(
            ServerTransactionComponent.Builder transactionComponentBuilder
    ) {
        this.transactionComponentBuilder = transactionComponentBuilder;
    }


    @Override
    public ServerResponseTransaction prepareCharacteristicTransaction(
            final byte[] value,
            final int requestID,
            final int offset,
            final BluetoothDevice device,
            final UUID characteristic
    ) {
        final ServerTransactionComponent.TransactionConfig config = new ServerTransactionComponent.TransactionConfig();
        config.offset = offset;
        config.requestID = requestID;
        config.value = value;
        final ServerTransactionComponent transactionComponent = transactionComponentBuilder
                .config(config)
                .device(device)
                .characteristic(characteristic)
                .build();
        return transactionComponent.getCharacteristicTransaction();
    }

    @Override
    public NotificationSetupTransaction prepareNotificationSetupTransaction(
            BluetoothDevice device,
            UUID characteristic
    ) {
        final ServerTransactionComponent transactionComponent = transactionComponentBuilder
                .device(device)
                .characteristic(characteristic)
                .build();
        return transactionComponent.getNotificationSetupTransaction();
    }
}
