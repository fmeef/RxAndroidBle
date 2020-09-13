package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;

import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.server.BluetoothGattServerProvider;

import io.reactivex.Observable;
import io.reactivex.Scheduler;

public class CharacteristicIndicationOperation extends NotifyCharacteristicChangedOperation {


    public CharacteristicIndicationOperation(
            Scheduler clientScheduler,
            BluetoothDevice device,
            BluetoothGattServerProvider serverProvider,
            Observable<Integer> notificationCompletedObservable,
            BluetoothGattCharacteristic characteristic,
            TimeoutConfiguration timeoutConfiguration
    ) {
        super(
                clientScheduler,
                device,
                serverProvider,
                notificationCompletedObservable,
                characteristic,
                timeoutConfiguration
        );
    }

    @Override
    public boolean isIndication() {
        return true;
    }
}
