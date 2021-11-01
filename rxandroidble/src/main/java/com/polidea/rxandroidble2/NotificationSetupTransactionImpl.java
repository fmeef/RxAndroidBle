package com.polidea.rxandroidble2;

import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternal;

import java.util.UUID;

import bleshadow.javax.inject.Inject;
import io.reactivex.Completable;
import io.reactivex.Flowable;

@ServerTransactionScope
public class NotificationSetupTransactionImpl implements NotificationSetupTransaction {
    private final RxBleDevice device;
    private final RxBleServerConnectionInternal connection;
    private final UUID characteristic;
    @Inject
    public NotificationSetupTransactionImpl(
            RxBleDevice device,
            RxBleServerConnectionInternal connection,
            UUID characteristic
    ) {
        this.device = device;
        this.connection = connection;
        this.characteristic = characteristic;
    }

    @Override
    public RxBleDevice getDevice() {
        return device;
    }

    @Override
    public Completable notify(Flowable<byte[]> notif) {
        return connection.getConnection().setupNotifications(characteristic, notif, device);
    }

    @Override
    public Completable indicate(Flowable<byte[]> notif) {
        return connection.getConnection().setupIndication(characteristic, notif, device);
    }
}
