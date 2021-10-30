package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import java.util.UUID;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;

/**
 * BLE connection handle for a single devices connected to the GATT server
 */
public interface RxBleServerConnection extends Disposable {
    Completable setupNotifications(UUID characteristic, Flowable<byte[]> notifications, BluetoothDevice device);

    Completable setupIndication(UUID characteristic, Flowable<byte[]> indications, BluetoothDevice device);

    Single<NotificationSetupTransaction> awaitNotifications(UUID characteristic);

    Observable<Integer> getOnMtuChanged();

    Observable<ServerResponseTransaction> getOnCharacteristicReadRequest(UUID characteristic);

    Observable<ServerResponseTransaction> getOnDescriptorWriteRequest(UUID characteristic, UUID descriptor);

    void disconnect(BluetoothDevice device);

    Observable<BluetoothDevice> observeDisconnect();

    Observable<BluetoothDevice> observeConnect();

}

