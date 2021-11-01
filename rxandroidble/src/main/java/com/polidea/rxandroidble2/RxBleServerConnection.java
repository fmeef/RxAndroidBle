package com.polidea.rxandroidble2;

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
    Completable setupNotifications(UUID characteristic, Flowable<byte[]> notifications, RxBleDevice device);

    Completable setupIndication(UUID characteristic, Flowable<byte[]> indications, RxBleDevice device);

    Single<NotificationSetupTransaction> awaitNotifications(UUID characteristic);

    Observable<Integer> getOnMtuChanged();

    Observable<ServerResponseTransaction> getOnCharacteristicReadRequest(UUID characteristic);

    Observable<ServerResponseTransaction> getOnDescriptorWriteRequest(UUID characteristic, UUID descriptor);

    Completable disconnect(RxBleDevice device);

    Observable<RxBleDevice> observeDisconnect();

    Observable<RxBleDevice> observeConnect();

}

