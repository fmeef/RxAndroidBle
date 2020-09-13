package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;

import com.polidea.rxandroidble2.internal.server.BluetoothGattServerProvider;

import io.reactivex.Observable;
import io.reactivex.Scheduler;

public class CharacteristicNotificationOperation extends NotifyCharacteristicChangedOperation {

    public CharacteristicNotificationOperation(
            Scheduler clientScheduler,
            BluetoothDevice device,
            BluetoothGattServerProvider serverProvider,
            Observable<Integer> notificationCompletedObservable,
            BluetoothGattCharacteristic characteristic
    ) {
        super(clientScheduler, device, serverProvider, notificationCompletedObservable, characteristic);
    }

    @Override
    public boolean isIndication() {
        return false;
    }
}
