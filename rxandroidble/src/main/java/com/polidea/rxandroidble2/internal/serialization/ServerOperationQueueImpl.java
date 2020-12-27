package com.polidea.rxandroidble2.internal.serialization;

import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.ServerConnectionComponent;
import com.polidea.rxandroidble2.ServerConnectionScope;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.connection.DisconnectionRouterOutput;
import com.polidea.rxandroidble2.internal.operations.Operation;
import com.polidea.rxandroidble2.internal.server.ServerConnectionSubscriptionWatcher;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.observers.DisposableObserver;

@ServerConnectionScope
public class ServerOperationQueueImpl extends OperationQueueBase implements
        ServerOperationQueue, ServerConnectionSubscriptionWatcher {

    private final DisconnectionRouterOutput disconnectionRouterOutput;
    private DisposableObserver<BleException> disconnectionThrowableSubscription;
    private BleException disconnectionException = null;
    private boolean shouldRun = true;

    @Inject
    public ServerOperationQueueImpl(
            @Named(ServerComponent.NamedSchedulers.BLUETOOTH_SERVER) final Scheduler callbackScheduler,
            @Named(ServerConnectionComponent.SERVER_DISCONNECTION_ROUTER) final DisconnectionRouterOutput disconnectionRouterOutput

    ) {
        super(callbackScheduler);
        this.disconnectionRouterOutput = disconnectionRouterOutput;
    }

    @Override
    public <T> Observable<T> queue(Operation<T> operation) {
        return super.queue(operation);
    }

    @Override
    public synchronized void terminate(BleException disconnectException) {
        if (this.disconnectionException != null) {
            // already terminated
            return;
        }
        RxBleLog.d(disconnectException, "Connection operations queue to be terminated");
        shouldRun = false;
        disconnectionException = disconnectException;
    }

    @Override
    public void onConnectionSubscribed() {
        disconnectionThrowableSubscription = disconnectionRouterOutput.asValueOnlyObservable()
                .subscribeWith(new DisposableObserver<BleException>() {
                    @Override
                    public void onNext(BleException e) {
                        terminate(e);
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    @Override
    public void onConnectionUnsubscribed() {
        disconnectionThrowableSubscription.dispose();
        disconnectionThrowableSubscription = null;
        terminate(new BleDisconnectedException("server"));
    }
}
