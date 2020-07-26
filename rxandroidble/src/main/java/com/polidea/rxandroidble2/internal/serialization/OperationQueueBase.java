package com.polidea.rxandroidble2.internal.serialization;

import androidx.annotation.RestrictTo;

import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.operations.Operation;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposables;
import io.reactivex.functions.Action;

import static com.polidea.rxandroidble2.internal.logger.LoggerUtil.logOperationFinished;
import static com.polidea.rxandroidble2.internal.logger.LoggerUtil.logOperationQueued;
import static com.polidea.rxandroidble2.internal.logger.LoggerUtil.logOperationRemoved;
import static com.polidea.rxandroidble2.internal.logger.LoggerUtil.logOperationRunning;
import static com.polidea.rxandroidble2.internal.logger.LoggerUtil.logOperationStarted;

public abstract class OperationQueueBase {

    final OperationPriorityFifoBlockingQueue queue = new OperationPriorityFifoBlockingQueue();

    public OperationQueueBase(final Scheduler callbackScheduler) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        final FIFORunnableEntry<?> entry = queue.take();
                        final Operation<?> operation = entry.operation;
                        final long startedAtTime = System.currentTimeMillis();
                        logOperationStarted(operation);
                        logOperationRunning(operation);

                        /*
                         * Calling bluetooth calls before the previous one returns in a callback usually finishes with a failure
                         * status. Below a QueueSemaphore is passed to the RxBleCustomOperation and is meant to be released
                         * at appropriate time when the next operation should be able to start successfully.
                         */
                        final QueueSemaphore clientOperationSemaphore = new QueueSemaphore();
                        entry.run(clientOperationSemaphore, callbackScheduler);
                        clientOperationSemaphore.awaitRelease();
                        logOperationFinished(operation, startedAtTime, System.currentTimeMillis());
                    } catch (InterruptedException e) {
                        RxBleLog.e(e, "Error while processing client operation queue");
                    }
                }
            }
        }).start();
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public <T> Observable<T> queue(final Operation<T> operation) {
        return Observable.create(new ObservableOnSubscribe<T>() {
            @Override
            public void subscribe(ObservableEmitter<T> tEmitter) {
                final FIFORunnableEntry entry = new FIFORunnableEntry<>(operation, tEmitter);

                tEmitter.setDisposable(Disposables.fromAction(new Action() {
                    @Override
                    public void run() {
                        if (queue.remove(entry)) {
                            logOperationRemoved(operation);
                        }
                    }
                }));

                logOperationQueued(operation);
                queue.add(entry);
            }
        });
    }
}
