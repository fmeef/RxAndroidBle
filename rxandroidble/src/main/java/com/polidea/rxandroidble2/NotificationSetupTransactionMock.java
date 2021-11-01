package com.polidea.rxandroidble2;

import io.reactivex.Completable;
import io.reactivex.Flowable;

public class NotificationSetupTransactionMock implements NotificationSetupTransaction {

    private final RxBleDevice device;

    public NotificationSetupTransactionMock(RxBleDevice device) {
        this.device = device;
    }

    @Override
    public RxBleDevice getDevice() {
        return device;
    }

    @Override
    public Completable notify(Flowable<byte[]> notif) {
        return Completable.complete();
    }

    @Override
    public Completable indicate(Flowable<byte[]> notif) {
        return Completable.complete();
    }
}
