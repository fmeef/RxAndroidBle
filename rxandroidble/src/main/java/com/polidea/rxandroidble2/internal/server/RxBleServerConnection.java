package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import androidx.annotation.NonNull;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.internal.util.ByteAssociation;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;

import io.reactivex.Observable;

/**
 * BLE connection handle for a single devices connected to the GATT server
 */
public interface RxBleServerConnection {
    @NonNull
    RxBleGattServerCallback.Output<ByteAssociation<UUID>> getReadCharacteristicOutput();

    @NonNull
    RxBleGattServerCallback.Output<ByteAssociation<UUID>> getWriteCharacteristicOutput();

    @NonNull
    RxBleGattServerCallback.Output<ByteAssociation<BluetoothGattDescriptor>> getReadDescriptorOutput();

    @NonNull
    RxBleGattServerCallback.Output<ByteAssociation<BluetoothGattDescriptor>> getWriteDescriptorOutput();

    @NonNull
    PublishRelay<RxBleConnection.RxBleConnectionState> getConnectionStatePublishRelay();

    @NonNull
    RxBleGattServerCallback.Output<BluetoothDevice> getNotificationPublishRelay();

    @NonNull
    RxBleGattServerCallback.Output<Integer> getChangedMtuOutput();

    boolean writeCharacteristicBytes(BluetoothGattCharacteristic characteristic, byte[] bytes);

    boolean writeDescriptorBytes(BluetoothGattDescriptor descriptor, byte[] bytes);

    @NonNull
    ByteArrayOutputStream getDescriptorLongWriteStream(BluetoothGattDescriptor descriptor);

    @NonNull
    Map<BluetoothGattCharacteristic, ByteArrayOutputStream> getCharacteristicLongWriteStreamMap();

    @NonNull
    Map<BluetoothGattDescriptor, ByteArrayOutputStream> getDescriptorByteArrayOutputStreamMap();

    void resetDescriptorMap();

    void resetCharacteristicMap();

    Observable<Integer> getOnMtuChanged(BluetoothDevice device);

    Observable<ByteAssociation<UUID>> getOnCharacteristicReadRequest(BluetoothDevice device);

    Observable<ByteAssociation<UUID>> getOnCharacteristicWriteRequest(BluetoothDevice device);

    Observable<ByteAssociation<BluetoothGattDescriptor>> getOnDescriptorReadRequest(BluetoothDevice device);

    Observable<ByteAssociation<BluetoothGattDescriptor>> getOnDescriptorWriteRequest(BluetoothDevice device);

    Observable<BluetoothDevice> getOnNotification(BluetoothDevice device);
}
