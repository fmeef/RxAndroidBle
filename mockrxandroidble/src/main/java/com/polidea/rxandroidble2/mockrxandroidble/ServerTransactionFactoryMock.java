package com.polidea.rxandroidble2.mockrxandroidble;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.NotificationSetupTransaction;
import com.polidea.rxandroidble2.NotificationSetupTransactionMock;
import com.polidea.rxandroidble2.ServerResponseTransaction;
import com.polidea.rxandroidble2.ServerTransactionFactory;

import java.util.concurrent.Callable;

import io.reactivex.Single;

public class ServerTransactionFactoryMock implements ServerTransactionFactory {

    private final boolean response;

    public ServerTransactionFactoryMock(boolean response) {
        this.response = response;
    }

    @Override
    public Single<ServerResponseTransaction> prepareCharacteristicTransaction(
            final byte[] value,
            final int requestID,
            final int offset,
            final BluetoothDevice device
    ) {
        return Single.fromCallable(new Callable<ServerResponseTransaction>() {
            @Override
            public ServerResponseTransaction call() throws Exception {
                ServerResponseTransaction transaction = new ServerResponseTransactionMock(requestID, offset, value, device, response);
                return transaction;
            }
        });
    }

    @Override
    public Single<NotificationSetupTransaction> prepareNotificationSetupTransaction(final BluetoothDevice device) {
        return Single.fromCallable(new Callable<NotificationSetupTransaction>() {
            @Override
            public NotificationSetupTransaction call() throws Exception {
                return new NotificationSetupTransactionMock(device);
            }
        });
    }
}
