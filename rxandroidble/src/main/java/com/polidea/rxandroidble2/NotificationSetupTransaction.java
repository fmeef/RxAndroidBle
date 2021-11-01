package com.polidea.rxandroidble2;

import io.reactivex.Completable;
import io.reactivex.Flowable;

public interface NotificationSetupTransaction {
    RxBleDevice getDevice();
    Completable notify(Flowable<byte[]> notif);
    Completable indicate(Flowable<byte[]> notif);
}
