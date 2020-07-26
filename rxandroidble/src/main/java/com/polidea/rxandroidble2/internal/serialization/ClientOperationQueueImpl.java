package com.polidea.rxandroidble2.internal.serialization;

import com.polidea.rxandroidble2.ClientComponent;
import com.polidea.rxandroidble2.internal.operations.Operation;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.Scheduler;

public class ClientOperationQueueImpl extends OperationQueueBase implements ClientOperationQueue {

    @Inject
    public ClientOperationQueueImpl(@Named(ClientComponent.NamedSchedulers.BLUETOOTH_INTERACTION) final Scheduler callbackScheduler) {
        super(callbackScheduler);
    }

    @Override
    public <T> Observable<T> queue(Operation<T> operation) {
        return super.queue(operation);
    }
}
