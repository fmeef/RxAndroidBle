package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProvider;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueue;

import bleshadow.javax.inject.Inject;
import io.reactivex.Completable;
import io.reactivex.CompletableSource;
import io.reactivex.functions.Function;

@ServerTransactionScope
public class ServerResponseTransactionImpl implements ServerResponseTransaction, Comparable<ServerResponseTransaction> {
    private final ServerOperationQueue operationQueue;
    private final ServerConnectionOperationsProvider operationsProvider;
    private final BluetoothDevice remoteDevice;
    private final byte[] value;
    private final int requestID;
    private final int offset;

    @Inject
    public ServerResponseTransactionImpl(
            ServerOperationQueue operationQueue,
            ServerConnectionOperationsProvider operationsProvider,
            ServerTransactionComponent.TransactionConfig config,
            BluetoothDevice device
    ) {
        this.operationQueue = operationQueue;
        this.operationsProvider = operationsProvider;
        this.value = config.value;
        this.requestID = config.requestID;
        this.offset = config.offset;
        this.remoteDevice = device;
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
    public Completable sendReply(byte[] value, int status) {
        RxBleLog.d("sendReply to remote: " + remoteDevice.getAddress());
        return operationQueue.queue(operationsProvider.provideReplyOperation(
                remoteDevice,
                requestID,
                status,
                0,
                value
        )).flatMapCompletable(new Function<Boolean, CompletableSource>() {
            @Override
            public CompletableSource apply(@io.reactivex.annotations.NonNull Boolean aBoolean) {
                if (aBoolean) {
                    return Completable.complete();
                } else {
                    return Completable.error(new BleException("reply failed"));
                }
            }
        });
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
