package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;

import io.reactivex.Observable;

public interface ServerOperationsProvider {

    ServerLongWriteOperation provideLongWriteOperation(Observable<byte[]> bytes, BluetoothDevice device);
    ServerReplyOperation provideReplyOperation(
            BluetoothDevice device,
            int requestID,
            int status,
            int offset,
            byte[] value
    );
}
