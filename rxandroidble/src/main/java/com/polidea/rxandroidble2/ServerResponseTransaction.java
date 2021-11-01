package com.polidea.rxandroidble2;

import io.reactivex.Completable;

public interface ServerResponseTransaction {
    int getRequestID();
    int compareTo(ServerResponseTransaction o);
    String toString();
    Completable sendReply(byte[] value, int status);
    byte[] getValue();
    RxBleDevice getRemoteDevice();
    int getOffset();
}
