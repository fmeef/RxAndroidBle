package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import androidx.annotation.NonNull;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.ServerConfig;
import com.polidea.rxandroidble2.ServerResponseTransaction;
import com.polidea.rxandroidble2.ServerTransactionFactory;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.internal.operations.server.CharacteristicNotificationOperation;
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProvider;
import com.polidea.rxandroidble2.internal.serialization.ServerConnectionOperationQueue;
import com.polidea.rxandroidble2.internal.util.GattServerTransaction;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;

public class RxBleServerConnectionImpl implements RxBleServerConnection {
    private final Scheduler callbackScheduler;
    private final ServerConnectionOperationsProvider operationsProvider;
    private final ServerConnectionOperationQueue operationQueue;
    private final BluetoothDevice device;
    private final ServerDisconnectionRouter disconnectionRouter;
    private final MultiIndex<Integer, BluetoothGattCharacteristic, LongWriteClosableOutput<byte[]>>
    characteristicMultiIndex = new MultiIndexImpl<>();
    private final MultiIndex<Integer, BluetoothGattDescriptor, LongWriteClosableOutput<byte[]>>
    descriptorMultiIndex = new MultiIndexImpl<>();
    final ServerTransactionFactory serverTransactionFactory;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final ServerConfig serverConfig;

    private final Function<BleException, Observable<?>> errorMapper = new Function<BleException, Observable<?>>() {
        @Override
        public Observable<?> apply(BleException bleGattException) {
            return Observable.error(bleGattException);
        }
    };

    @Inject
    public RxBleServerConnectionImpl(
        @Named(ServerComponent.NamedSchedulers.BLUETOOTH_CONNECTION) Scheduler callbackScheduler,
        ServerConnectionOperationsProvider operationsProvider,
        ServerConnectionOperationQueue operationQueue,
        BluetoothDevice device,
        ServerDisconnectionRouter disconnectionRouter,
        ServerTransactionFactory serverTransactionFactory,
        ServerConfig serverConfig

    ) {
        this.callbackScheduler = callbackScheduler;
        this.operationsProvider = operationsProvider;
        this.device = device;
        this.operationQueue = operationQueue;
        this.disconnectionRouter = disconnectionRouter;
        this.serverTransactionFactory = serverTransactionFactory;
        this.serverConfig = serverConfig;
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
    private final Output<Integer> notificationPublishRelay =
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
    public Output<Integer> getNotificationPublishRelay() {
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

    @NonNull
    @Override
    public LongWriteClosableOutput<byte[]> openLongWriteCharacteristicOutput(
            Integer requestid,
            BluetoothGattCharacteristic characteristic
    ) {
        LongWriteClosableOutput<byte[]> output = characteristicMultiIndex.get(requestid);
        if (output == null) {
            output = new LongWriteClosableOutput<>();
            output.valueRelay
                    .reduce(new BiFunction<byte[], byte[], byte[]>() {
                        @Override
                        public byte[] apply(byte[] first, byte[] second) throws Exception {
                            byte[] both = Arrays.copyOf(first, first.length + second.length);
                            System.arraycopy(second, 0, both, first.length, second.length);
                            return both;
                        }
                    })
                    .subscribeOn(callbackScheduler)
                    .toSingle()
                    .subscribe(output.out);
            characteristicMultiIndex.put(requestid, output);
            characteristicMultiIndex.putMulti(characteristic, output);
        }
        return output;
    }

    @NonNull
    @Override
    public LongWriteClosableOutput<byte[]> openLongWriteDescriptorOutput(Integer requestid, BluetoothGattDescriptor descriptor) {
        LongWriteClosableOutput<byte[]> output = descriptorMultiIndex.get(requestid);
        if (output == null) {
            output = new LongWriteClosableOutput<>();
            output.valueRelay
                    .reduce(new BiFunction<byte[], byte[], byte[]>() {
                        @Override
                        public byte[] apply(byte[] first, byte[] second) throws Exception {
                            byte[] both = Arrays.copyOf(first, first.length + second.length);
                            System.arraycopy(second, 0, both, first.length, second.length);
                            return both;
                        }
                    })
                    .subscribeOn(callbackScheduler)
                    .toSingle()
                    .subscribe(output.out);
            descriptorMultiIndex.put(requestid, output);
            descriptorMultiIndex.putMulti(descriptor, output);
        }
        return output;
    }

    @Override
    public Single<byte[]> closeLongWriteCharacteristicOutput(Integer requestid) {
        LongWriteClosableOutput<byte[]> output = characteristicMultiIndex.get(requestid);
        if (output != null) {
            output.valueRelay.onComplete();
            characteristicMultiIndex.remove(requestid);
            return output.out.delay(0, TimeUnit.SECONDS, callbackScheduler);
        }
        return Single.never();
    }

    @Override
    public Single<byte[]> closeLongWriteDescriptorOutput(Integer requestid) {
        LongWriteClosableOutput<byte[]> output = descriptorMultiIndex.get(requestid);
        if (output != null) {
            output.valueRelay.onComplete();
            characteristicMultiIndex.remove(requestid);
            return output.out.delay(0, TimeUnit.SECONDS, callbackScheduler);
        }
        return Single.never();
    }

    @Override
    public Single<byte[]> getLongWriteCharacteristicObservable(Integer requestid) {
        LongWriteClosableOutput<byte[]> output = characteristicMultiIndex.get(requestid);
        if (output != null) {
            return output.out.delay(0, TimeUnit.SECONDS, callbackScheduler);
        } else {
            return Single.never();
        }
    }

    @Override
    public Single<byte[]> getLongWriteDescriptorObservable(Integer requestid) {
        LongWriteClosableOutput<byte[]> output = descriptorMultiIndex.get(requestid);
        if (output != null) {
            return output.out.delay(0, TimeUnit.SECONDS, callbackScheduler);
        } else {
            return Single.never();
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

    @Override
    public Observable<Integer> setupNotifications(final BluetoothGattCharacteristic characteristic, Observable<byte[]> notifications) {
        return notifications
                .flatMap(new Function<byte[], ObservableSource<Integer>>() {
                    @Override
                    public ObservableSource<Integer> apply(byte[] bytes) throws Exception {
                        characteristic.setValue(bytes);
                        CharacteristicNotificationOperation operation = operationsProvider.provideCharacteristicNotificationOperation(
                                characteristic,
                                getOnNotification()
                        );
                        return operationQueue.queue(operation);
                    }
                });
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
    @Override
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
    public Observable<Integer> getOnNotification() {
        return withDisconnectionHandling(getNotificationPublishRelay())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    @Override
    public void disconnect() {
        operationQueue.queue(operationsProvider.provideDisconnectOperation(device));
    }

    @Override
    public void prepareDescriptorTransaction(
            final BluetoothGattDescriptor descriptor,
            int requestID,
            int offset, BluetoothDevice device,
            PublishRelay<GattServerTransaction<BluetoothGattDescriptor>> valueRelay
    ) {
        Disposable disposable = serverTransactionFactory.prepareCharacteristicTransaction(
                descriptor.getValue(),
                requestID,
                offset,
                device
        )
                .map(new Function<ServerResponseTransaction, GattServerTransaction<BluetoothGattDescriptor>>() {
                    @Override
                    public GattServerTransaction<BluetoothGattDescriptor> apply(
                            ServerResponseTransaction serverResponseTransaction) throws Exception {
                        return new GattServerTransaction<>(descriptor, serverResponseTransaction);
                    }
                })
                .subscribeOn(callbackScheduler)
                .subscribe(valueRelay);
        compositeDisposable.add(disposable);
    }

    @Override
    public void prepareCharacteristicTransaction(
            final BluetoothGattCharacteristic characteristic,
            int requestID,
            int offset, BluetoothDevice device,
            PublishRelay<GattServerTransaction<UUID>> valueRelay
    ) {
        Disposable disposable = serverTransactionFactory.prepareCharacteristicTransaction(
                characteristic.getValue(),
                requestID,
                offset,
                device
        )
                .map(new Function<ServerResponseTransaction, GattServerTransaction<UUID>>() {
                    @Override
                    public GattServerTransaction<UUID> apply(
                            ServerResponseTransaction serverResponseTransaction) throws Exception {
                        return new GattServerTransaction<>(characteristic.getUuid(), serverResponseTransaction);
                    }
                })
                .subscribeOn(callbackScheduler)
                .subscribe(valueRelay);
        compositeDisposable.add(disposable);
    }
}
