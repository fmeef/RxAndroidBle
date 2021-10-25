package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import io.reactivex.Completable;
import io.reactivex.Flowable;

public interface NotificationSetupTransaction {
    BluetoothDevice getDevice();
    Completable notify(Flowable<byte[]> notif);
    Completable indicate(Flowable<byte[]> notif);
}
