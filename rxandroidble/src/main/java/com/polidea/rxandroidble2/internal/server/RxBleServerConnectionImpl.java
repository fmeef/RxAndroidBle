package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.internal.operations.server.ServerLongWriteOperation;
import com.polidea.rxandroidble2.internal.operations.server.ServerOperationsProvider;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueue;
import com.polidea.rxandroidble2.internal.util.GattServerTransaction;

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
    private final ServerDisconnectionRouter disconnectionRouter;
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
        BluetoothDevice device,
        ServerDisconnectionRouter disconnectionRouter

    ) {
        this.callbackScheduler = callbackScheduler;
        this.operationsProvider = operationsProvider;
        this.device = device;
        this.operationQueue = operationQueue;
        this.disconnectionRouter = disconnectionRouter;
    }


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
    private final Output<BluetoothDevice> notificationPublishRelay =
            new Output<>();
    private final Output<Integer> changedMtuOutput =
            new Output<>();

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
    public PublishRelay<RxBleConnection.RxBleConnectionState> getConnectionStatePublishRelay() {
        return connectionStatePublishRelay;
    }

    @NonNull
    @Override
    public Output<BluetoothDevice> getNotificationPublishRelay() {
        return notificationPublishRelay;
    }

    @NonNull
    @Override
    public BluetoothDevice getDevice() {
        return device;
    }

    @NonNull
    @Override
    public Output<Integer> getChangedMtuOutput() {
        return changedMtuOutput;
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
    public Observable<byte[]> getLongWriteObservable(Integer requestid) {
        Pair<LongWriteClosableOutput<byte[]>, Observable<byte[]>> output
                = characteristicMultiIndex.get(requestid);
        if (output != null) {
            return output.second;
        } else {
            return null;
        }
    }

    @Override
    public void resetDescriptorMap() {
        descriptorMultiIndex.clear();
    }

    @Override
    public void resetCharacteristicMap() {
        characteristicMultiIndex.clear();
    }


    private <T> Observable<T> withDisconnectionHandling(Output<T> output) {
        //noinspection unchecked
        return Observable.merge(
                disconnectionRouter.<T>asErrorOnlyObservable(),
                output.valueRelay,
                (Observable<T>) output.errorRelay.flatMap(errorMapper)
        );
    }

    @Override
    public Observable<Integer> getOnMtuChanged() {

        return withDisconnectionHandling(getChangedMtuOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    @Override
    public ServerDisconnectionRouter getDisconnectionRouter() {
        return disconnectionRouter;
    }

    /**
     * @return Observable that never emits onNext.
     * @throws BleDisconnectedException emitted in case of a disconnect that is a part of the normal flow
     * @throws BleGattServerException         emitted in case of connection was interrupted unexpectedly.
     */
    public <T> Observable<T> observeDisconnect() {
        return disconnectionRouter.asErrorOnlyObservable();
    }

    @Override
    public Observable<GattServerTransaction<UUID>> getOnCharacteristicReadRequest() {
        return withDisconnectionHandling(getReadCharacteristicOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    @Override
    public Observable<GattServerTransaction<UUID>> getOnCharacteristicWriteRequest() {
        return withDisconnectionHandling(getWriteCharacteristicOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    @Override
    public Observable<GattServerTransaction<BluetoothGattDescriptor>> getOnDescriptorReadRequest() {
        return withDisconnectionHandling(getReadDescriptorOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    @Override
    public Observable<GattServerTransaction<BluetoothGattDescriptor>> getOnDescriptorWriteRequest() {
        return withDisconnectionHandling(getWriteDescriptorOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    @Override
    public Observable<BluetoothDevice> getOnNotification() {
        return withDisconnectionHandling(getNotificationPublishRelay())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }
}
