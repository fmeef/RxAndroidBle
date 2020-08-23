package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import androidx.annotation.NonNull;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.ServerConnectionScope;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.internal.util.TransactionAssociation;

import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

/**
 * BLE connection handle for a single devices connected to the GATT server
 */
@ServerConnectionScope
public interface RxBleServerConnection {
    @NonNull
    Output<TransactionAssociation<UUID>> getReadCharacteristicOutput();

    @NonNull
    Output<TransactionAssociation<UUID>> getWriteCharacteristicOutput();

    @NonNull
    Output<TransactionAssociation<BluetoothGattDescriptor>> getReadDescriptorOutput();

    @NonNull
    Output<TransactionAssociation<BluetoothGattDescriptor>> getWriteDescriptorOutput();

    @NonNull
    Output<BluetoothDevice> getNotificationPublishRelay();

    @NonNull
    Output<Integer> getChangedMtuOutput();

    Output<byte[]> openLongWriteOutput(Integer requestid, BluetoothGattCharacteristic characteristic);
    Output<byte[]> openLongWriteOutput(Integer requestid, BluetoothGattDescriptor descriptor);

    Observable<byte[]> closeLongWriteOutput(Integer requestid);

    Observable<byte[]> getLongWriteObservable(Integer requestid);

    void resetDescriptorMap();

    void resetCharacteristicMap();

    Observable<Integer> getOnMtuChanged(BluetoothDevice device);

    Observable<TransactionAssociation<UUID>> getOnCharacteristicReadRequest(BluetoothDevice device);

    Observable<TransactionAssociation<UUID>> getOnCharacteristicWriteRequest(BluetoothDevice device);

    Observable<TransactionAssociation<BluetoothGattDescriptor>> getOnDescriptorReadRequest(BluetoothDevice device);

    Observable<TransactionAssociation<BluetoothGattDescriptor>> getOnDescriptorWriteRequest(BluetoothDevice device);

    Observable<BluetoothDevice> getOnNotification(BluetoothDevice device);

    class Output<T> {

        final PublishRelay<T> valueRelay;
        final PublishRelay<BleGattServerException> errorRelay;

        Output() {
            this.valueRelay = PublishRelay.create();
            this.errorRelay = PublishRelay.create();
        }

        boolean hasObservers() {
            return valueRelay.hasObservers() || errorRelay.hasObservers();
        }
    }

    class LongWriteClosableOutput<T> extends Output<T> {

        final Subject<T> valueRelay;

        LongWriteClosableOutput() {
            super();
            this.valueRelay = PublishSubject.create();
        }

        public void finalize() {
            this.valueRelay.onComplete();
        }
    }
}
