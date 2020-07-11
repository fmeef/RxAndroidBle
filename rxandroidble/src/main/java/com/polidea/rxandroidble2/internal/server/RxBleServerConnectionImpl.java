package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import androidx.annotation.NonNull;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.ClientComponent;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.internal.operations.OperationsProvider;
import com.polidea.rxandroidble2.internal.util.ByteAssociation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.functions.Function;

public class RxBleServerConnectionImpl implements RxBleServerConnection {
    private final OperationsProvider operationsProvider;
    private final Scheduler callbackScheduler;

    private final Function<BleGattServerException, Observable<?>> errorMapper = new Function<BleGattServerException, Observable<?>>() {
        @Override
        public Observable<?> apply(BleGattServerException bleGattException) {
            return Observable.error(bleGattException);
        }
    };

    @Inject
    public RxBleServerConnectionImpl(
        OperationsProvider operationsProvider,
        @Named(ClientComponent.NamedSchedulers.BLUETOOTH_CALLBACKS) Scheduler callbackScheduler
    ) {
        this.operationsProvider = operationsProvider;
        this.callbackScheduler = callbackScheduler;
    }


    private final RxBleGattServerCallback.Output<ByteAssociation<UUID>> readCharacteristicOutput =
            new RxBleGattServerCallback.Output<>();
    private final RxBleGattServerCallback.Output<ByteAssociation<UUID>> writeCharacteristicOutput =
            new RxBleGattServerCallback.Output<>();
    private final RxBleGattServerCallback.Output<ByteAssociation<BluetoothGattDescriptor>> readDescriptorOutput =
            new RxBleGattServerCallback.Output<>();
    private final RxBleGattServerCallback.Output<ByteAssociation<BluetoothGattDescriptor>> writeDescriptorOutput =
            new RxBleGattServerCallback.Output<>();
    private final PublishRelay<RxBleConnection.RxBleConnectionState> connectionStatePublishRelay =
            PublishRelay.create();
    private final RxBleGattServerCallback.Output<BluetoothDevice> notificationPublishRelay =
            new RxBleGattServerCallback.Output<>();
    private final RxBleGattServerCallback.Output<Integer> changedMtuOutput =
            new RxBleGattServerCallback.Output<>();
    private final Map<BluetoothGattCharacteristic, ByteArrayOutputStream> characteristicByteArrayOutputStreamMap =
            new HashMap<>();
    private final Map<BluetoothGattDescriptor, ByteArrayOutputStream> descriptorByteArrayOutputStreamMap =
            new HashMap<>();

    @NonNull
    public RxBleGattServerCallback.Output<ByteAssociation<UUID>> getReadCharacteristicOutput() {
        return readCharacteristicOutput;
    }

    @NonNull
    public RxBleGattServerCallback.Output<ByteAssociation<UUID>> getWriteCharacteristicOutput() {
        return writeCharacteristicOutput;
    }

    @NonNull
    public RxBleGattServerCallback.Output<ByteAssociation<BluetoothGattDescriptor>> getReadDescriptorOutput() {
        return readDescriptorOutput;
    }

    @NonNull
    public RxBleGattServerCallback.Output<ByteAssociation<BluetoothGattDescriptor>> getWriteDescriptorOutput() {
        return writeDescriptorOutput;
    }

    @NonNull
    public PublishRelay<RxBleConnection.RxBleConnectionState> getConnectionStatePublishRelay() {
        return connectionStatePublishRelay;
    }

    @NonNull
    public RxBleGattServerCallback.Output<BluetoothDevice> getNotificationPublishRelay() {
        return notificationPublishRelay;
    }

    @NonNull
    public RxBleGattServerCallback.Output<Integer> getChangedMtuOutput() {
        return changedMtuOutput;
    }

    public boolean writeCharacteristicBytes(BluetoothGattCharacteristic characteristic, byte[] bytes) {
        try {
            ByteArrayOutputStream os = characteristicByteArrayOutputStreamMap.get(characteristic);
            if (os == null) {
                return false;
            }
            os.write(bytes);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public boolean writeDescriptorBytes(BluetoothGattDescriptor descriptor, byte[] bytes) {
        try {
            ByteArrayOutputStream os = descriptorByteArrayOutputStreamMap.get(descriptor);
            if (os == null) {
                return false;
            }
            os.write(bytes);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    @NonNull
    public ByteArrayOutputStream getDescriptorLongWriteStream(BluetoothGattDescriptor descriptor) {
        ByteArrayOutputStream os = descriptorByteArrayOutputStreamMap.get(descriptor);
        if (os == null) {
            os = new ByteArrayOutputStream();
            descriptorByteArrayOutputStreamMap.put(descriptor, os);
        }

        return os;
    }

    @NonNull
    public Map<BluetoothGattCharacteristic, ByteArrayOutputStream> getCharacteristicLongWriteStreamMap() {
        return characteristicByteArrayOutputStreamMap;
    }

    @NonNull
    public Map<BluetoothGattDescriptor, ByteArrayOutputStream> getDescriptorByteArrayOutputStreamMap() {
        return descriptorByteArrayOutputStreamMap;
    }

    public void resetDescriptorMap() {
        descriptorByteArrayOutputStreamMap.clear();
    }

    public void resetCharacteristicMap() {
        characteristicByteArrayOutputStreamMap.clear();
    }


    private <T> Observable<T> withDisconnectionHandling(RxBleGattServerCallback.Output<T> output) {
        //noinspection unchecked
        return Observable.merge(
                output.valueRelay,
                (Observable<T>) output.errorRelay.flatMap(errorMapper)
        );
    }

    public Observable<Integer> getOnMtuChanged(BluetoothDevice device) {

        return withDisconnectionHandling(getChangedMtuOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }



    public Observable<ByteAssociation<UUID>> getOnCharacteristicReadRequest(BluetoothDevice device) {
        return withDisconnectionHandling(getReadCharacteristicOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    public Observable<ByteAssociation<UUID>> getOnCharacteristicWriteRequest(BluetoothDevice device) {
        return withDisconnectionHandling(getWriteCharacteristicOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    public Observable<ByteAssociation<BluetoothGattDescriptor>> getOnDescriptorReadRequest(BluetoothDevice device) {
        return withDisconnectionHandling(getReadDescriptorOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    public Observable<ByteAssociation<BluetoothGattDescriptor>> getOnDescriptorWriteRequest(BluetoothDevice device) {
        return withDisconnectionHandling(getWriteDescriptorOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    public Observable<BluetoothDevice> getOnNotification(BluetoothDevice device) {
        return withDisconnectionHandling(getNotificationPublishRelay())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

}
