package com.polidea.rxandroidble2.internal.serialization;

import com.polidea.rxandroidble2.ClientComponent;
import com.polidea.rxandroidble2.ServerConnectionScope;
import com.polidea.rxandroidble2.internal.operations.Operation;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.Scheduler;

@ServerConnectionScope
public class ServerOperationQueueImpl extends OperationQueueBase implements
        ServerOperationQueue {

    @Inject
    public ServerOperationQueueImpl(
            @Named(ClientComponent.NamedSchedulers.BLUETOOTH_CALLBACKS) final Scheduler callbackScheduler
    ) {
        super(callbackScheduler);
    }

    @Override
    public <T> Observable<T> queue(Operation<T> operation) {
        return super.queue(operation);
    }
}
