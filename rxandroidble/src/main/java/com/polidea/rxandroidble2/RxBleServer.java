package com.polidea.rxandroidble2;

import android.content.Context;

import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;

public abstract class RxBleServer extends RxBleBase {

    public static RxBleServer create(@NonNull Context context) {
        return DaggerServerComponent
                .builder()
                .applicationContext(context)
                .build()
                .rxBleServer();
    }

    /**
     * Obtain instance of RxBleDevice for provided MAC address. This may be the same instance that was provided during scan operation but
     * this in not guaranteed.
     *
     * @param macAddress Bluetooth LE device MAC address.
     * @return Handle for Bluetooth LE operations.
     * @throws UnsupportedOperationException if called on system without Bluetooth capabilities
     */
    public abstract RxBleDevice getBleDevice(@androidx.annotation.NonNull String macAddress);

    public interface GattServerSessionBuilder {
        GattServerSessionBuilder setBytesObservable(Observable<Byte[]> bytesObservable);

        GattServerSessionBuilder setResponseNeeded(boolean responseNeeded);

        GattServerSessionBuilder setRequestId(int requestId);

        Observable<byte[]> build();
    }
}
