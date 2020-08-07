package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;

import io.reactivex.Observable;

public interface ServerOperationsProvider {

    ServerLongWriteOperation provideLongWriteOperation(Observable<byte[]> bytes, BluetoothDevice device);

}
