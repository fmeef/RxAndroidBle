package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import androidx.annotation.NonNull;

import com.polidea.rxandroidble2.internal.util.GattServerTransaction;

import java.util.UUID;

import io.reactivex.Observable;

/**
 * BLE connection handle for a single devices connected to the GATT server
 */
public interface RxBleServerConnection {
    @NonNull
    BluetoothDevice getDevice();

    Observable<Integer> setupNotifications(BluetoothGattCharacteristic characteristic, Observable<byte[]> notifications);

    Observable<Integer> getOnMtuChanged();

    Observable<GattServerTransaction<UUID>> getOnCharacteristicReadRequest(BluetoothGattCharacteristic characteristic);

    Observable<GattServerTransaction<UUID>> getOnCharacteristicWriteRequest(BluetoothGattCharacteristic characteristic);

    Observable<GattServerTransaction<BluetoothGattDescriptor>> getOnDescriptorReadRequest(BluetoothGattDescriptor descriptor);

    Observable<GattServerTransaction<BluetoothGattDescriptor>> getOnDescriptorWriteRequest(BluetoothGattDescriptor descriptor);

    Observable<Integer> getOnNotification();

    void disconnect();

    <T> Observable<T> observeDisconnect();

    Observable<Boolean> blindAck(
            int requestID,
            int status,
            byte[] value
    );
}

