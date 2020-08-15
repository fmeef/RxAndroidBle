package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

import com.polidea.rxandroidble2.internal.operations.server.ServerOperationsProvider;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueue;

import bleshadow.javax.inject.Inject;
import io.reactivex.Observable;

@ServerTransactionScope
public class ServerResponseTransactionImpl implements ServerResponseTransaction, Comparable<ServerResponseTransaction> {
    private final ServerOperationQueue operationQueue;
    private final ServerOperationsProvider operationsProvider;
    private final BluetoothDevice remoteDevice;
    private final byte[] value;
    private final int requestID;
    private final int offset;

    @Inject
    public ServerResponseTransactionImpl(
            ServerOperationQueue operationQueue,
            ServerOperationsProvider operationsProvider,
            ServerTransactionComponent.TransactionConfig config
    ) {
        this.operationQueue = operationQueue;
        this.operationsProvider = operationsProvider;
        this.value = config.value;
        this.requestID = config.requestID;
        this.offset = config.offset;
        this.remoteDevice = config.device;
    }

    @Override
    public int compareTo(ServerResponseTransaction o) {
        return o.getRequestID() - requestID;
    }

    @Override
    public int getRequestID() {
        return requestID;
    }

    @Override
    public Observable<Boolean> sendReply(int status, int offset, byte[] value) {
        return operationQueue.queue(operationsProvider.provideReplyOperation(
                remoteDevice,
                requestID,
                status,
                offset,
                value
        ));
    }

    @NonNull
    @Override
    public String toString() {
        return "Transaction: id " + requestID;
    }

    @Override
    public byte[] getValue() {
        return value;
    }

    @Override
    public BluetoothDevice getRemoteDevice() {
        return remoteDevice;
    }

    @Override
    public int getOffset() {
        return offset;
    }
}
