package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.ServerConnectionComponent;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.internal.operations.server.ServerLongWriteOperation;
import com.polidea.rxandroidble2.internal.operations.server.ServerOperationsProvider;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueue;
import com.polidea.rxandroidble2.internal.util.ByteAssociation;

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
    private final Scheduler callbackScheduler;
    private final ServerOperationsProvider operationsProvider;
    private final ServerOperationQueue operationQueue;
    private final BluetoothDevice device;

    private final Function<BleGattServerException, Observable<?>> errorMapper = new Function<BleGattServerException, Observable<?>>() {
        @Override
        public Observable<?> apply(BleGattServerException bleGattException) {
            return Observable.error(bleGattException);
        }
    };

    @Inject
    public RxBleServerConnectionImpl(
        @Named(ServerConnectionComponent.NamedSchedulers.BLUETOOTH_CALLBACKS) Scheduler callbackScheduler,
        ServerOperationsProvider operationsProvider,
        ServerOperationQueue operationQueue,
        BluetoothDevice device
    ) {
        this.callbackScheduler = callbackScheduler;
        this.operationsProvider = operationsProvider;
        this.device = device;
        this.operationQueue = operationQueue;
    }


    private final Output<ByteAssociation<UUID>> readCharacteristicOutput =
            new Output<>();
    private final Output<ByteAssociation<UUID>> writeCharacteristicOutput =
            new Output<>();
    private final Output<ByteAssociation<BluetoothGattDescriptor>> readDescriptorOutput =
            new Output<>();
    private final Output<ByteAssociation<BluetoothGattDescriptor>> writeDescriptorOutput =
            new Output<>();
    private final PublishRelay<RxBleConnection.RxBleConnectionState> connectionStatePublishRelay =
            PublishRelay.create();
    private final Output<BluetoothDevice> notificationPublishRelay =
            new Output<>();
    private final Output<Integer> changedMtuOutput =
            new Output<>();
    private final Map<BluetoothGattCharacteristic, Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>>>
            characteristicLongWriteMap = new HashMap<>();
    private final Map<BluetoothGattDescriptor, Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>>>
            descriptorLongWriteMapMap = new HashMap<>();

    @NonNull
    public Output<ByteAssociation<UUID>> getReadCharacteristicOutput() {
        return readCharacteristicOutput;
    }

    @NonNull
    public Output<ByteAssociation<UUID>> getWriteCharacteristicOutput() {
        return writeCharacteristicOutput;
    }

    @NonNull
    public Output<ByteAssociation<BluetoothGattDescriptor>> getReadDescriptorOutput() {
        return readDescriptorOutput;
    }

    @NonNull
    public Output<ByteAssociation<BluetoothGattDescriptor>> getWriteDescriptorOutput() {
        return writeDescriptorOutput;
    }

    @NonNull
    public PublishRelay<RxBleConnection.RxBleConnectionState> getConnectionStatePublishRelay() {
        return connectionStatePublishRelay;
    }

    @NonNull
    public Output<BluetoothDevice> getNotificationPublishRelay() {
        return notificationPublishRelay;
    }

    @NonNull
    public Output<Integer> getChangedMtuOutput() {
        return changedMtuOutput;
    }

    public Output<byte[]> openLongWriteOutput(BluetoothGattCharacteristic characteristic) {
        if (!characteristicLongWriteMap.containsKey(characteristic)) {
            LongWriteClosableOutput<byte[]> output = new LongWriteClosableOutput<>();
            ServerLongWriteOperation operation
                    = operationsProvider.provideLongWriteOperation(output.valueRelay, device);
            Observable<byte[]> operationResult = operationQueue.queue(operation);
            Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>> mapresult
                    = new Pair<>(output, operationResult);
            characteristicLongWriteMap.put(characteristic, mapresult);
            return output;
        } else {
            Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>> r
                    = characteristicLongWriteMap.get(characteristic);
            if (r != null) {
                return r.first;
            } else {
                return null;
            }
        }
    }

    public Output<byte[]> openLongWriteOutput(BluetoothGattDescriptor descriptor) {
        if (!descriptorLongWriteMapMap.containsKey(descriptor)) {
            LongWriteClosableOutput<byte[]> output = new LongWriteClosableOutput<>();
            ServerLongWriteOperation operation
                    = operationsProvider.provideLongWriteOperation(output.valueRelay, device);
            Observable<byte[]> operationResult =  operationQueue.queue(operation);
            Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>> mapresult
                    = new Pair<>(output, operationResult);
            descriptorLongWriteMapMap.put(descriptor, mapresult);
            return output;
        } else {
            Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>> r
            = descriptorLongWriteMapMap.get(descriptor);
            if (r != null) {
                return r.first;
            } else {
                return null;
            }
        }
    }

    public Observable<byte[]> closeLongWriteOutput(BluetoothGattCharacteristic characteristic) {
        Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>> output
                = characteristicLongWriteMap.get(characteristic);
        if (output != null) {
            output.first.valueRelay.onComplete();
            characteristicLongWriteMap.remove(characteristic);
            return output.second;
        }
        return null;
    }

    public Observable<byte[]> closeLongWriteOutput(BluetoothGattDescriptor descriptor) {
        Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>> output
                = descriptorLongWriteMapMap.get(descriptor);
        if (output != null) {
            output.first.valueRelay.onComplete();
            descriptorLongWriteMapMap.remove(descriptor);
            return output.second;
        } else {
            return  null;
        }
    }

    public Observable<byte[]> getLongWriteObservable(BluetoothGattCharacteristic characteristic) {
        Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>> output
                = characteristicLongWriteMap.get(characteristic);
        if (output != null) {
            return output.second;
        } else {
            return null;
        }
    }

    public Observable<byte[]> getLongWriteObservable(BluetoothGattDescriptor descriptor) {
        Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>> output
                = descriptorLongWriteMapMap.get(descriptor);
        if (output != null) {
            return output.second;
        } else {
            return  null;
        }
    }

    public void resetDescriptorMap() {
        descriptorLongWriteMapMap.clear();
    }

    public void resetCharacteristicMap() {
        characteristicLongWriteMap.clear();
    }


    private <T> Observable<T> withDisconnectionHandling(Output<T> output) {
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
