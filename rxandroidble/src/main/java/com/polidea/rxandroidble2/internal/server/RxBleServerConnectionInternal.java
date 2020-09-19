package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import androidx.annotation.NonNull;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.ServerConnectionScope;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.internal.util.GattServerTransaction;

import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.SingleSubject;
import io.reactivex.subjects.Subject;

/**
 * BLE connection handle for a single devices connected to the GATT server
 */
@ServerConnectionScope
public interface RxBleServerConnectionInternal {
    @NonNull
    Output<GattServerTransaction<UUID>> getReadCharacteristicOutput();

    @NonNull
    Output<GattServerTransaction<UUID>> getWriteCharacteristicOutput();

    @NonNull
    Output<GattServerTransaction<BluetoothGattDescriptor>> getReadDescriptorOutput();

    @NonNull
    Output<GattServerTransaction<BluetoothGattDescriptor>> getWriteDescriptorOutput();

    @NonNull
    Output<Integer> getNotificationPublishRelay();

    @NonNull
    Output<Integer> getChangedMtuOutput();

    @NonNull
    BluetoothDevice getDevice();

    @NonNull
    ServerDisconnectionRouter getDisconnectionRouter();

    @NonNull
    Output<byte[]> openLongWriteCharacteristicOutput(Integer requestid, BluetoothGattCharacteristic characteristic);

    @NonNull
    Output<byte[]> openLongWriteDescriptorOutput(Integer requestid, BluetoothGattDescriptor descriptor);

    Single<byte[]> closeLongWriteCharacteristicOutput(Integer requestid);

    Single<byte[]> closeLongWriteDescriptorOutput(Integer requestid);

    Single<byte[]> getLongWriteCharacteristicObservable(Integer requestid);

    Single<byte[]> getLongWriteDescriptorObservable(Integer requestsid);

    void resetDescriptorMap();

    void resetCharacteristicMap();

    Observable<Integer> setupNotifications(BluetoothGattCharacteristic characteristic, Observable<byte[]> notifications);

    Observable<Integer> getOnMtuChanged();

    Observable<GattServerTransaction<UUID>> getOnCharacteristicReadRequest(BluetoothGattCharacteristic characteristic);

    Observable<GattServerTransaction<UUID>> getOnCharacteristicWriteRequest(BluetoothGattCharacteristic characteristic);

    Observable<GattServerTransaction<BluetoothGattDescriptor>> getOnDescriptorReadRequest(BluetoothGattDescriptor descriptor);

    Observable<GattServerTransaction<BluetoothGattDescriptor>> getOnDescriptorWriteRequest(BluetoothGattDescriptor descriptor);

    Observable<Integer> getOnNotification();

    void disconnect();

    <T> Observable<T> observeDisconnect();

    void prepareDescriptorTransaction(
            BluetoothGattDescriptor descriptor,
            int requestID,
            int offset,
            BluetoothDevice device,
            PublishRelay<GattServerTransaction<BluetoothGattDescriptor>> valueRelay
    );

    void prepareCharacteristicTransaction(
            BluetoothGattCharacteristic descriptor,
            int requestID,
            int offset,
            BluetoothDevice device,
            PublishRelay<GattServerTransaction<UUID>> valueRelay
    );

    class Output<T> {

        final PublishRelay<T> valueRelay;
        final PublishRelay<BleGattServerException> errorRelay;

        public Output() {
            this.valueRelay = PublishRelay.create();
            this.errorRelay = PublishRelay.create();
        }

        boolean hasObservers() {
            return valueRelay.hasObservers() || errorRelay.hasObservers();
        }
    }

   class LongWriteClosableOutput<T> extends Output<T> {

        final Subject<T> valueRelay;
        final SingleSubject<T> out;

        public LongWriteClosableOutput() {
            super();
            this.valueRelay = PublishSubject.create();
            this.out = SingleSubject.create();
        }

        public void finalize() {
            this.valueRelay.onComplete();
        }
    }
}
