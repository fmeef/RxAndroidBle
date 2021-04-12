package com.polidea.rxandroidble2.internal.serialization;

import com.polidea.rxandroidble2.ServerComponent;
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
            @Named(ServerComponent.NamedSchedulers.BLUETOOTH_SERVER) final Scheduler callbackScheduler
    ) {
        super(callbackScheduler);
    }

    @Override
    public <T> Observable<T> queue(Operation<T> operation) {
        return super.queue(operation);
    }
}
