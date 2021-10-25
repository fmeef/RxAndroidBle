package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternal;

import java.util.UUID;

import bleshadow.javax.inject.Inject;
import io.reactivex.Completable;
import io.reactivex.Flowable;

@ServerTransactionScope
public class NotificationSetupTransactionImpl implements NotificationSetupTransaction {
    private final BluetoothDevice device;
    private final RxBleServerConnectionInternal connection;
    @Inject
    public NotificationSetupTransactionImpl(
            ServerTransactionComponent.TransactionConfig device,
            RxBleServerConnectionInternal connection
    ) {
        this.device = device.device;
        this.connection = connection;
    }

    @Override
    public BluetoothDevice getDevice() {
        return device;
    }

    @Override
    public Completable notify(UUID characteristic, Flowable<byte[]> notif) {
        return connection.getConnection().setupNotifications(characteristic, notif, device);
    }

    @Override
    public Completable indicate(UUID characteristic, Flowable<byte[]> notif) {
        return connection.getConnection().setupIndication(characteristic, notif, device);
    }
}
