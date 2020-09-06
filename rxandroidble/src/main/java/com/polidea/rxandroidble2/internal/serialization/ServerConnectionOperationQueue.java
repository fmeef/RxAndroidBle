package com.polidea.rxandroidble2.internal.serialization;

import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.internal.operations.Operation;

import io.reactivex.Observable;

public interface ServerConnectionOperationQueue {
    void terminate(BleException disconnectException);

    /**
     * Function that queues an {@link Operation} for execution.
     * @param operation the operation to execute
     * @param <T> type of the operation values
     * @return the observable representing the operation execution
     */
    <T> Observable<T> queue(Operation<T> operation);
}
