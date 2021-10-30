package com.polidea.rxandroidble2.mockrxandroidble;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.NotificationSetupTransaction;
import com.polidea.rxandroidble2.NotificationSetupTransactionMock;
import com.polidea.rxandroidble2.ServerResponseTransaction;
import com.polidea.rxandroidble2.ServerTransactionFactory;

import java.util.UUID;

public class ServerTransactionFactoryMock implements ServerTransactionFactory {

    private final boolean response;

    public ServerTransactionFactoryMock(boolean response) {
        this.response = response;
    }

    @Override
    public ServerResponseTransaction prepareCharacteristicTransaction(
            final byte[] value,
            final int requestID,
            final int offset,
            final BluetoothDevice device,
            final UUID ch
    ) {
        return new ServerResponseTransactionMock(requestID, offset, value, device, response);
    }

    @Override
    public NotificationSetupTransaction prepareNotificationSetupTransaction(
            final BluetoothDevice device,
            final UUID characteristic
            ) {
        return new NotificationSetupTransactionMock(device);
    }
}
