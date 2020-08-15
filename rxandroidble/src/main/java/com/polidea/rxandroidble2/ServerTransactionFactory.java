package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import io.reactivex.Observable;

public interface ServerTransactionFactory {
    Observable<ServerResponseTransaction> prepareCharacteristicTransaction(
            byte[] value,
            int requestID,
            int offset,
            BluetoothDevice device
    );
}
