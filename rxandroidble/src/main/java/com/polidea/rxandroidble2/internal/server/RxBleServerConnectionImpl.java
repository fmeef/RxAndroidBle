package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.internal.operations.server.ServerLongWriteOperation;
import com.polidea.rxandroidble2.internal.operations.server.ServerOperationsProvider;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueue;
import com.polidea.rxandroidble2.internal.util.TransactionAssociation;

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
    private final MultiIndex<Integer, BluetoothGattCharacteristic, Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>>>
    characteristicMultiIndex = new MultiIndexImpl<>();
    private final MultiIndex<Integer, BluetoothGattDescriptor, Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>>>
    descriptorMultiIndex = new MultiIndexImpl<>();

    private final Function<BleGattServerException, Observable<?>> errorMapper = new Function<BleGattServerException, Observable<?>>() {
        @Override
        public Observable<?> apply(BleGattServerException bleGattException) {
            return Observable.error(bleGattException);
        }
    };

    @Inject
    public RxBleServerConnectionImpl(
        @Named(ServerComponent.NamedSchedulers.BLUETOOTH_CONNECTION) Scheduler callbackScheduler,
        ServerOperationsProvider operationsProvider,
        ServerOperationQueue operationQueue,
        BluetoothDevice device
    ) {
        this.callbackScheduler = callbackScheduler;
        this.operationsProvider = operationsProvider;
        this.device = device;
        this.operationQueue = operationQueue;
    }


    private final Output<TransactionAssociation<UUID>> readCharacteristicOutput =
            new Output<>();
    private final Output<TransactionAssociation<UUID>> writeCharacteristicOutput =
            new Output<>();
    private final Output<TransactionAssociation<BluetoothGattDescriptor>> readDescriptorOutput =
            new Output<>();
    private final Output<TransactionAssociation<BluetoothGattDescriptor>> writeDescriptorOutput =
            new Output<>();
    private final PublishRelay<RxBleConnection.RxBleConnectionState> connectionStatePublishRelay =
            PublishRelay.create();
    private final Output<BluetoothDevice> notificationPublishRelay =
            new Output<>();
    private final Output<Integer> changedMtuOutput =
            new Output<>();
    @NonNull
    public Output<TransactionAssociation<UUID>> getReadCharacteristicOutput() {
        return readCharacteristicOutput;
    }

    @NonNull
    public Output<TransactionAssociation<UUID>> getWriteCharacteristicOutput() {
        return writeCharacteristicOutput;
    }

    @NonNull
    public Output<TransactionAssociation<BluetoothGattDescriptor>> getReadDescriptorOutput() {
        return readDescriptorOutput;
    }

    @NonNull
    public Output<TransactionAssociation<BluetoothGattDescriptor>> getWriteDescriptorOutput() {
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
    public BluetoothDevice getDevice() {
        return device;
    }

    @NonNull
    public Output<Integer> getChangedMtuOutput() {
        return changedMtuOutput;
    }

    public Output<byte[]> openLongWriteOutput(Integer requestid, BluetoothGattCharacteristic characteristic) {
        if (!characteristicMultiIndex.containsKey(requestid)) {
            LongWriteClosableOutput<byte[]> output = new LongWriteClosableOutput<>();
            ServerLongWriteOperation operation
                    = operationsProvider.provideLongWriteOperation(output.valueRelay, device);
            Observable<byte[]> operationResult = operationQueue.queue(operation);
            Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>> mapresult
                    = new Pair<>(output, operationResult);
            characteristicMultiIndex.put(requestid, mapresult);
            characteristicMultiIndex.putMulti(characteristic, mapresult);
            return output;
        } else {
            Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>> r
                    = characteristicMultiIndex.get(requestid);
            if (r != null) {
                return r.first;
            } else {
                return null;
            }
        }
    }

    public Output<byte[]> openLongWriteOutput(Integer requestid, BluetoothGattDescriptor descriptor) {
        if (!descriptorMultiIndex.containsKey(requestid)) {
            LongWriteClosableOutput<byte[]> output = new LongWriteClosableOutput<>();
            ServerLongWriteOperation operation
                    = operationsProvider.provideLongWriteOperation(output.valueRelay, device);
            Observable<byte[]> operationResult =  operationQueue.queue(operation);
            Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>> mapresult
                    = new Pair<>(output, operationResult);
            descriptorMultiIndex.put(requestid, mapresult);
            descriptorMultiIndex.putMulti(descriptor, mapresult);
            return output;
        } else {
            Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>> r
            = descriptorMultiIndex.get(requestid);
            if (r != null) {
                return r.first;
            } else {
                return null;
            }
        }
    }

    public Observable<byte[]> closeLongWriteOutput(Integer requestid) {
        Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>> output
                = characteristicMultiIndex.get(requestid);
        if (output != null) {
            output.first.valueRelay.onComplete();
            characteristicMultiIndex.remove(requestid);
            return output.second;
        }
        return null;
    }

    public Observable<byte[]> getLongWriteObservable(Integer requestid) {
        Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>> output
                = characteristicMultiIndex.get(requestid);
        if (output != null) {
            return output.second;
        } else {
            return null;
        }
    }

    public void resetDescriptorMap() {
        descriptorMultiIndex.clear();
    }

    public void resetCharacteristicMap() {
        characteristicMultiIndex.clear();
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



    public Observable<TransactionAssociation<UUID>> getOnCharacteristicReadRequest(BluetoothDevice device) {
        return withDisconnectionHandling(getReadCharacteristicOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    public Observable<TransactionAssociation<UUID>> getOnCharacteristicWriteRequest(BluetoothDevice device) {
        return withDisconnectionHandling(getWriteCharacteristicOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    public Observable<TransactionAssociation<BluetoothGattDescriptor>> getOnDescriptorReadRequest(BluetoothDevice device) {
        return withDisconnectionHandling(getReadDescriptorOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    public Observable<TransactionAssociation<BluetoothGattDescriptor>> getOnDescriptorWriteRequest(BluetoothDevice device) {
        return withDisconnectionHandling(getWriteDescriptorOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    public Observable<BluetoothDevice> getOnNotification(BluetoothDevice device) {
        return withDisconnectionHandling(getNotificationPublishRelay())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }
}
