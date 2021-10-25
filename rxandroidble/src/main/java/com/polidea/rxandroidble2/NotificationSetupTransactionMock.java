package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import java.util.UUID;

import io.reactivex.Completable;
import io.reactivex.Flowable;

public class NotificationSetupTransactionMock implements NotificationSetupTransaction {

    private final BluetoothDevice device;

    public NotificationSetupTransactionMock(BluetoothDevice device) {
        this.device = device;
    }

    @Override
    public BluetoothDevice getDevice() {
        return device;
    }

    @Override
    public Completable notify(UUID characteristic, Flowable<byte[]> notif) {
        return Completable.complete();
    }

    @Override
    public Completable indicate(UUID characteristic, Flowable<byte[]> notif) {
        return Completable.complete();
    }
}
