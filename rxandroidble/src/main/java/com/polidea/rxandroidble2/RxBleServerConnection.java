package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import androidx.annotation.NonNull;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;

/**
 * BLE connection handle for a single devices connected to the GATT server
 */
public interface RxBleServerConnection {
    @NonNull
    BluetoothDevice getDevice();

    Completable setupNotifications(BluetoothGattCharacteristic characteristic,
                                   Single<Flowable<byte[]>> notifications);

    Completable setupIndication(BluetoothGattCharacteristic characteristic, Single<Flowable<byte[]>> indications);

    Observable<Integer> getOnMtuChanged();

    Observable<ServerResponseTransaction> getOnCharacteristicReadRequest(BluetoothGattCharacteristic characteristic);

    Observable<ServerResponseTransaction> getOnCharacteristicWriteRequest(BluetoothGattCharacteristic characteristic);

    Observable<ServerResponseTransaction> getOnDescriptorReadRequest(BluetoothGattDescriptor descriptor);

    Observable<ServerResponseTransaction> getOnDescriptorWriteRequest(BluetoothGattDescriptor descriptor);

    Single<Integer> indicationSingle(BluetoothGattCharacteristic characteristic, byte[] value);

    Single<Integer> notificationSingle(BluetoothGattCharacteristic characteristic, byte[] value);

    Observable<Integer> getOnNotification();

    void disconnect();

    <T> Observable<T> observeDisconnect();

    Observable<Boolean> blindAck(
            int requestID,
            int status,
            byte[] value
    );
}

