package com.polidea.rxandroidble2.mockrxandroidble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import androidx.annotation.NonNull;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleServerConnection;
import com.polidea.rxandroidble2.ServerResponseTransaction;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternal;
import com.polidea.rxandroidble2.internal.util.GattServerTransaction;

import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.Single;

public class RxBleServerConnectionMock implements RxBleServerConnection, RxBleServerConnectionInternal {


    private final Output<GattServerTransaction<UUID>> readCharacteristicOutput =
            new Output<>();
    private final Output<GattServerTransaction<UUID>> writeCharacteristicOutput =
            new Output<>();
    private final Output<GattServerTransaction<BluetoothGattDescriptor>> readDescriptorOutput =
            new Output<>();
    private final Output<GattServerTransaction<BluetoothGattDescriptor>> writeDescriptorOutput =
            new Output<>();
    private final PublishRelay<RxBleConnection.RxBleConnectionState> connectionStatePublishRelay =
            PublishRelay.create();
    private final Output<Integer> notificationPublishRelay =
            new Output<>();
    private final Output<Integer> changedMtuOutput =
            new Output<>();


    public RxBleServerConnectionMock() {

    }

    @NonNull
    @Override
    public BluetoothDevice getDevice() {
        return null;
    }

    @Override
    public Observable<Integer> setupNotifications(BluetoothGattCharacteristic characteristic, Observable<byte[]> notifications) {
        return null;
    }

    @Override
    public Observable<Integer> setupIndication(BluetoothGattCharacteristic characteristic, Observable<byte[]> indications) {
        return null;
    }

    @Override
    public Observable<Integer> getOnMtuChanged() {
        return null;
    }

    @Override
    public Observable<ServerResponseTransaction> getOnCharacteristicReadRequest(
            BluetoothGattCharacteristic characteristic
    ) {
        return null;
    }

    @Override
    public Observable<ServerResponseTransaction> getOnCharacteristicWriteRequest(
            BluetoothGattCharacteristic characteristic
    ) {
        return null;
    }

    @Override
    public Observable<ServerResponseTransaction> getOnDescriptorReadRequest(
            BluetoothGattDescriptor descriptor
    ) {
        return null;
    }

    @Override
    public Observable<ServerResponseTransaction> getOnDescriptorWriteRequest(
            BluetoothGattDescriptor descriptor
    ) {
        return null;
    }

    @Override
    public Single<Integer> indicationSingle(BluetoothGattCharacteristic characteristic, byte[] value) {
        return null;
    }

    @Override
    public Single<Integer> notificationSingle(BluetoothGattCharacteristic characteristic, byte[] value) {
        return null;
    }

    @Override
    public Observable<Integer> getOnNotification() {
        return null;
    }

    @Override
    public void disconnect() {

    }

    @Override
    public <T> Observable<T> observeDisconnect() {
        return null;
    }

    @Override
    public Observable<Boolean> blindAck(int requestID, int status, byte[] value) {
        return null;
    }

    @NonNull
    @Override
    public Output<GattServerTransaction<UUID>> getReadCharacteristicOutput() {
        return readCharacteristicOutput;
    }

    @NonNull
    @Override
    public Output<GattServerTransaction<UUID>> getWriteCharacteristicOutput() {
        return writeCharacteristicOutput;
    }

    @NonNull
    @Override
    public Output<GattServerTransaction<BluetoothGattDescriptor>> getReadDescriptorOutput() {
        return readDescriptorOutput;
    }

    @NonNull
    @Override
    public Output<GattServerTransaction<BluetoothGattDescriptor>> getWriteDescriptorOutput() {
        return writeDescriptorOutput;
    }

    @NonNull
    @Override
    public Output<Integer> getNotificationPublishRelay() {
        return notificationPublishRelay;
    }

    @NonNull
    @Override
    public Output<Integer> getChangedMtuOutput() {
        return changedMtuOutput;
    }

    @Override
    public void onGattConnectionStateException(BleGattServerException exception) {

    }

    @Override
    public void onDisconnectedException(BleDisconnectedException exception) {

    }

    @NonNull
    @Override
    public Output<byte[]> openLongWriteCharacteristicOutput(Integer requestid, BluetoothGattCharacteristic characteristic) {
        return null;
    }

    @NonNull
    @Override
    public Output<byte[]> openLongWriteDescriptorOutput(Integer requestid, BluetoothGattDescriptor descriptor) {
        return null;
    }

    @Override
    public Single<byte[]> closeLongWriteCharacteristicOutput(Integer requestid) {
        return null;
    }

    @Override
    public Single<byte[]> closeLongWriteDescriptorOutput(Integer requestid) {
        return null;
    }

    @Override
    public void resetDescriptorMap() {

    }

    @Override
    public void resetCharacteristicMap() {

    }

    @Override
    public RxBleServerConnection getConnection() {
        return null;
    }

    @Override
    public void prepareDescriptorTransaction(
            BluetoothGattDescriptor descriptor,
            int requestID,
            int offset,
            BluetoothDevice device,
            PublishRelay<GattServerTransaction<BluetoothGattDescriptor>> valueRelay,
            byte[] value
    ) {

    }

    @Override
    public void prepareCharacteristicTransaction(
            BluetoothGattCharacteristic descriptor,
            int requestID,
            int offset,
            BluetoothDevice device,
            PublishRelay<GattServerTransaction<UUID>> valueRelay,
            byte[] value) {

    }
}
