package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import java.util.UUID;

import io.reactivex.Completable;
import io.reactivex.Flowable;

public interface NotificationSetupTransaction {
    BluetoothDevice getDevice();
    Completable notify(UUID characteristic, Flowable<byte[]> notif);
    Completable indicate(UUID characteristic, Flowable<byte[]> notif);
}
