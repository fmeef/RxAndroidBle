package com.polidea.rxandroidble2;

import android.content.Context;

import com.polidea.rxandroidble2.internal.server.RxBleServerConnection;

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

    public abstract Observable<RxBleServerConnection> openServer();

    public abstract void closeServer();
}
