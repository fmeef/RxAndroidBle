package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

import java.util.UUID;

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

    Completable setupNotifications(UUID characteristic, Flowable<byte[]> notifications);

    Completable setupIndication(UUID characteristic, Flowable<byte[]> indications);

    Observable<Integer> getOnMtuChanged();

    Observable<ServerResponseTransaction> getOnCharacteristicReadRequest(UUID characteristic);

    Observable<ServerResponseTransaction> getOnCharacteristicWriteRequest(UUID characteristic);

    Observable<ServerResponseTransaction> getOnDescriptorReadRequest(UUID characteristic, UUID descriptor);

    Observable<ServerResponseTransaction> getOnDescriptorWriteRequest(UUID characteristic, UUID descriptor);

    Single<Integer> indicationSingle(UUID characteristic, byte[] value);

    Single<Integer> notificationSingle(UUID characteristic, byte[] value);

    Observable<Integer> getOnNotification();

    void disconnect();

    <T> Observable<T> observeDisconnect();

    Observable<Boolean> blindAck(
            int requestID,
            int status,
            byte[] value
    );
}

