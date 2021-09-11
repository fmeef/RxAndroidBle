package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import io.reactivex.Completable;

public interface ServerResponseTransaction {
    int getRequestID();
    int compareTo(ServerResponseTransaction o);
    String toString();
    Completable sendReply(byte[] value, int status);
    byte[] getValue();
    BluetoothDevice getRemoteDevice();
    int getOffset();
}
