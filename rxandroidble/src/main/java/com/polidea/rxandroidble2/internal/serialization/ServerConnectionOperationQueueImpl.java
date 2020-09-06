package com.polidea.rxandroidble2.internal.serialization;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.ServerConnectionScope;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.connection.ConnectionSubscriptionWatcher;
import com.polidea.rxandroidble2.internal.connection.DisconnectionRouterOutput;
import com.polidea.rxandroidble2.internal.operations.Operation;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.observers.DisposableObserver;

import static com.polidea.rxandroidble2.internal.logger.LoggerUtil.commonMacMessage;

@ServerConnectionScope
public class ServerConnectionOperationQueueImpl extends OperationQueueBase implements
        ServerConnectionOperationQueue, ServerOperationQueue, ConnectionSubscriptionWatcher {

    private final BluetoothDevice device;
    private final DisconnectionRouterOutput disconnectionRouterOutput;
    private DisposableObserver<BleException> disconnectionThrowableSubscription;
    private BleException disconnectionException = null;
    private boolean shouldRun = true;

    @Inject
    public ServerConnectionOperationQueueImpl(
            @Named(ServerComponent.NamedSchedulers.BLUETOOTH_INTERACTION) final Scheduler callbackScheduler,
            BluetoothDevice device,
            final DisconnectionRouterOutput disconnectionRouterOutput

    ) {
        super(callbackScheduler);
        this.disconnectionRouterOutput = disconnectionRouterOutput;
        this.device = device;
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
        RxBleLog.d(disconnectException, "Connection operations queue to be terminated (%s)", commonMacMessage(device.getAddress()));
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
        terminate(new BleDisconnectedException(device.getAddress(), BleDisconnectedException.UNKNOWN_STATUS));
    }
}
