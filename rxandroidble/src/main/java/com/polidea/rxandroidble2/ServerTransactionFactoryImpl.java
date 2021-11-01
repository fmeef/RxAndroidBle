package com.polidea.rxandroidble2;


import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.internal.RxBleDeviceProvider;

import java.util.UUID;

import bleshadow.javax.inject.Inject;

public class ServerTransactionFactoryImpl implements ServerTransactionFactory {
    final ServerTransactionComponent.Builder transactionComponentBuilder;
    final RxBleDeviceProvider deviceProvider;

    @Inject
    public ServerTransactionFactoryImpl(
            ServerTransactionComponent.Builder transactionComponentBuilder,
            RxBleDeviceProvider deviceProvider
    ) {
        this.transactionComponentBuilder = transactionComponentBuilder;
        this.deviceProvider = deviceProvider;
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
                .device(deviceProvider.getBleDevice(device.getAddress()))
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
                .device(deviceProvider.getBleDevice(device.getAddress()))
                .characteristic(characteristic)
                .build();
        return transactionComponent.getNotificationSetupTransaction();
    }

    @Override
    public ServerResponseTransaction prepareCharacteristicTransaction(
            byte[] value,
            int requestID,
            int offset,
            RxBleDevice device,
            UUID characteristic
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
    public NotificationSetupTransaction prepareNotificationSetupTransaction(RxBleDevice device, UUID characteristic) {
        final ServerTransactionComponent transactionComponent = transactionComponentBuilder
                .device(device)
                .characteristic(characteristic)
                .build();
        return transactionComponent.getNotificationSetupTransaction();
    }
}
