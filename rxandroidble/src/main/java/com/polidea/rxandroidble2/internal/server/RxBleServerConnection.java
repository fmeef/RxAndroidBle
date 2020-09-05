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
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

/**
 * BLE connection handle for a single devices connected to the GATT server
 */
@ServerConnectionScope
public interface RxBleServerConnection {
    @NonNull
    Output<GattServerTransaction<UUID>> getReadCharacteristicOutput();

    @NonNull
    Output<GattServerTransaction<UUID>> getWriteCharacteristicOutput();

    @NonNull
    Output<GattServerTransaction<BluetoothGattDescriptor>> getReadDescriptorOutput();

    @NonNull
    Output<GattServerTransaction<BluetoothGattDescriptor>> getWriteDescriptorOutput();

    @NonNull
    Output<BluetoothDevice> getNotificationPublishRelay();

    @NonNull
    Output<Integer> getChangedMtuOutput();

    @NonNull
    BluetoothDevice getDevice();

    @NonNull
    ServerDisconnectionRouter getDisconnectionRouter();

    Output<byte[]> openLongWriteOutput(Integer requestid, BluetoothGattCharacteristic characteristic);
    Output<byte[]> openLongWriteOutput(Integer requestid, BluetoothGattDescriptor descriptor);

    Observable<byte[]> closeLongWriteOutput(Integer requestid);

    Observable<byte[]> getLongWriteObservable(Integer requestid);

    void resetDescriptorMap();

    void resetCharacteristicMap();

    Observable<Integer> getOnMtuChanged();

    Observable<GattServerTransaction<UUID>> getOnCharacteristicReadRequest();

    Observable<GattServerTransaction<UUID>> getOnCharacteristicWriteRequest();

    Observable<GattServerTransaction<BluetoothGattDescriptor>> getOnDescriptorReadRequest();

    Observable<GattServerTransaction<BluetoothGattDescriptor>> getOnDescriptorWriteRequest();

    Observable<BluetoothDevice> getOnNotification();

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

        public LongWriteClosableOutput() {
            super();
            this.valueRelay = PublishSubject.create();
        }

        public void finalize() {
            this.valueRelay.onComplete();
        }
    }
}
