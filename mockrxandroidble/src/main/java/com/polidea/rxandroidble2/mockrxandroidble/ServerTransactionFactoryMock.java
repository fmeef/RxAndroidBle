package com.polidea.rxandroidble2.mockrxandroidble;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.ServerResponseTransaction;
import com.polidea.rxandroidble2.ServerTransactionFactory;

import java.util.concurrent.Callable;

import io.reactivex.Observable;

public class ServerTransactionFactoryMock implements ServerTransactionFactory {

    private final boolean response;

    public ServerTransactionFactoryMock(boolean response) {
        this.response = response;
    }

    @Override
    public Observable<ServerResponseTransaction> prepareCharacteristicTransaction(
            final byte[] value,
            final int requestID,
            final int offset,
            final BluetoothDevice device
    ) {
        return Observable.fromCallable(new Callable<ServerResponseTransaction>() {
            @Override
            public ServerResponseTransaction call() throws Exception {
                ServerResponseTransaction transaction = new ServerResponseTransactionMock(requestID, offset, value, device, response);
                return transaction;
            }
        });
    }
}
