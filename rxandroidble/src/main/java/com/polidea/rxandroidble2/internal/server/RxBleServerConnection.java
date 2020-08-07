package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import androidx.annotation.NonNull;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.internal.connection.ServerConnectionScope;
import com.polidea.rxandroidble2.internal.util.ByteAssociation;

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
    Output<ByteAssociation<UUID>> getReadCharacteristicOutput();

    @NonNull
    Output<ByteAssociation<UUID>> getWriteCharacteristicOutput();

    @NonNull
    Output<ByteAssociation<BluetoothGattDescriptor>> getReadDescriptorOutput();

    @NonNull
    Output<ByteAssociation<BluetoothGattDescriptor>> getWriteDescriptorOutput();

    @NonNull
    PublishRelay<RxBleConnection.RxBleConnectionState> getConnectionStatePublishRelay();

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

    Observable<ByteAssociation<UUID>> getOnCharacteristicReadRequest(BluetoothDevice device);

    Observable<ByteAssociation<UUID>> getOnCharacteristicWriteRequest(BluetoothDevice device);

    Observable<ByteAssociation<BluetoothGattDescriptor>> getOnDescriptorReadRequest(BluetoothDevice device);

    Observable<ByteAssociation<BluetoothGattDescriptor>> getOnDescriptorWriteRequest(BluetoothDevice device);

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
