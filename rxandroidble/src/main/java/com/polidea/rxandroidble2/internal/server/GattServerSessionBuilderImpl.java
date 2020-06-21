package com.polidea.rxandroidble2.internal.server;

import com.polidea.rxandroidble2.RxBleServer;

import bleshadow.javax.inject.Inject;
import io.reactivex.subjects.PublishSubject;

public class GattServerSessionBuilderImpl implements RxBleServer.GattServerSessionBuilder {
    private PublishSubject<Byte[]> fragmentPublishSubject;

    @Inject
    public GattServerSessionBuilderImpl() {
        fragmentPublishSubject = PublishSubject.create();
    }
}
