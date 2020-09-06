package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;

import io.reactivex.Observable;

public interface ServerConnectionOperationsProvider {
    ServerReplyOperation provideReplyOperation(
            BluetoothDevice device,
            int requestID,
            int status,
            int offset,
            byte[] value
    );
    ServerLongWriteOperation provideLongWriteOperation(Observable<byte[]> bytes, BluetoothDevice device);

}
