package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothGattCharacteristic;

import com.polidea.rxandroidble2.RxBleDevice;

public interface ServerConnectionOperationsProvider {
    ServerReplyOperation provideReplyOperation(
            RxBleDevice device,
            int requestID,
            int status,
            int offset,
            byte[] value
    );

    ServerDisconnectOperation provideDisconnectOperation(RxBleDevice device);

    NotifyCharacteristicChangedOperation provideNotifyOperation(
            BluetoothGattCharacteristic characteristic,
            byte[] value,
            boolean isIndication,
            RxBleDevice device
    );
}
