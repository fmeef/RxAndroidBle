package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleServerConnection;
import com.polidea.rxandroidble2.ServerConnectionScope;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.internal.util.GattServerTransaction;

import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.SingleSubject;
import io.reactivex.subjects.Subject;

/**
 * BLE connection handle for a single devices connected to the GATT server
 */
@ServerConnectionScope
public interface RxBleServerConnectionInternal extends Disposable {
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

    void onGattConnectionStateException(BleGattServerException exception);

    void onDisconnectedException(BleDisconnectedException exception);

    @NonNull
    Output<byte[]> openLongWriteCharacteristicOutput(Integer requestid, BluetoothGattCharacteristic characteristic);

    @NonNull
    Output<byte[]> openLongWriteDescriptorOutput(Integer requestid, BluetoothGattDescriptor descriptor);

    Single<byte[]> closeLongWriteCharacteristicOutput(Integer requestid);

    Single<byte[]> closeLongWriteDescriptorOutput(Integer requestid);

    void resetDescriptorMap();

    void resetCharacteristicMap();

    Observable<Integer> getOnNotification();

    void disconnect(BluetoothDevice device);

    RxBleServerConnection getConnection();

    <T> Observable<T> observeDisconnect();

    Observable<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>> getOnConnectionStateChange();


    void prepareDescriptorTransaction(
            BluetoothGattDescriptor descriptor,
            int requestID,
            int offset,
            BluetoothDevice device,
            PublishRelay<GattServerTransaction<BluetoothGattDescriptor>> valueRelay,
            byte[] value
    );

    void prepareCharacteristicTransaction(
            BluetoothGattCharacteristic descriptor,
            int requestID,
            int offset,
            BluetoothDevice device,
            PublishRelay<GattServerTransaction<UUID>> valueRelay,
            byte[] value
    );

    Observable<Boolean> blindAck(
            int requestID,
            int status,
            byte[] value,
            BluetoothDevice device
    );

    class Output<T> {

        public final PublishRelay<T> valueRelay;
        public final PublishRelay<BleGattServerException> errorRelay;

        public Output() {
            this.valueRelay = PublishRelay.create();
            this.errorRelay = PublishRelay.create();
        }

        public boolean hasObservers() {
            return valueRelay.hasObservers() || errorRelay.hasObservers();
        }
    }

   class LongWriteClosableOutput<T> extends Output<T> {

        public final Subject<T> valueRelay;
        public final SingleSubject<T> out;

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
