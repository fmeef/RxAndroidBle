package com.polidea.rxandroidble2.internal.server;

import com.polidea.rxandroidble2.RxBleServer;

import bleshadow.javax.inject.Inject;
import io.reactivex.Observable;

public class GattServerSessionBuilderImpl implements RxBleServer.GattServerSessionBuilder {
    private Observable<Byte[]> dataObservable;
    private boolean responseNeeded;
    private int requestId;

    @Inject
    public GattServerSessionBuilderImpl() {

    }

    @Override
    public RxBleServer.GattServerSessionBuilder setBytesObservable(Observable<Byte[]> bytesObservable) {
        this.dataObservable = bytesObservable;
        return this;
    }

    @Override
    public RxBleServer.GattServerSessionBuilder setResponseNeeded(boolean responseNeeded) {
        this.responseNeeded = responseNeeded;
        return this;
    }

    @Override
    public RxBleServer.GattServerSessionBuilder setRequestId(int requestId) {
        this.requestId = requestId;
        return this;
    }

    @Override
    public Observable<byte[]> build() {
        return null;
    }
}
