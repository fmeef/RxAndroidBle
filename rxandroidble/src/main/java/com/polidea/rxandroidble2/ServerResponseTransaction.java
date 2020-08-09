package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import io.reactivex.Observable;

public interface ServerResponseTransaction {
    int getRequestID();
    int compareTo(ServerResponseTransaction o);
    String toString();
    Observable<Boolean> sendReply(int status, int offset, byte[] value);
    byte[] getValue();
    BluetoothDevice getRemoteDevice();
    int getOffset();
}
