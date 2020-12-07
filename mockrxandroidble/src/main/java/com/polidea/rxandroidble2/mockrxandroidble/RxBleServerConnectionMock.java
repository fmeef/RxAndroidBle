package com.polidea.rxandroidble2.mockrxandroidble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleServer;
import com.polidea.rxandroidble2.RxBleServerConnection;
import com.polidea.rxandroidble2.ServerResponseTransaction;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.exceptions.BleGattServerOperationType;
import com.polidea.rxandroidble2.internal.server.MultiIndex;
import com.polidea.rxandroidble2.internal.server.MultiIndexImpl;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternal;
import com.polidea.rxandroidble2.internal.server.RxBleServerState;
import com.polidea.rxandroidble2.internal.util.GattServerTransaction;

import org.reactivestreams.Publisher;

import java.util.Arrays;
import java.util.Queue;
import java.util.UUID;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;

public class RxBleServerConnectionMock implements RxBleServerConnection, RxBleServerConnectionInternal {


    private final BluetoothDevice device;
    private final RxBleServerState serverState;

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

    private final Queue<Integer> notificationResults;
    private final Queue<Integer> indicationResults;

    private final MultiIndex<Integer, BluetoothGattCharacteristic, LongWriteClosableOutput<byte[]>>
            characteristicMultiIndex = new MultiIndexImpl<>();
    private final MultiIndex<Integer, BluetoothGattDescriptor, LongWriteClosableOutput<byte[]>>
            descriptorMultiIndex = new MultiIndexImpl<>();

    private final BehaviorRelay<BleException> disconnectionBehaviorRelay = BehaviorRelay.create();
    private final Observable<Object> disconnectionObservable;
    private final Observable<BleException> disconnectionExceptionObservable;

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    private final ServerTransactionFactoryMock serverTransactionFactory = new ServerTransactionFactoryMock(true);

    private final Function<BleException, Observable<?>> errorMapper = new Function<BleException, Observable<?>>() {
        @Override
        public Observable<?> apply(BleException bleGattException) {
            return Observable.error(bleGattException);
        }
    };

    private RxBleServerConnectionMock(
            BluetoothDevice device,
            RxBleServerState serverState,
            @Nullable Queue<Integer> notificationResults,
            @Nullable Queue<Integer> indicationResults
    ) {
        this.device = device;
        this.serverState = serverState;
        this.notificationResults = notificationResults;
        this.indicationResults = indicationResults;
        disconnectionExceptionObservable = disconnectionBehaviorRelay
                .firstElement()
                .toObservable()
                .replay()
                .autoConnect(0);
        disconnectionObservable = disconnectionExceptionObservable
                .flatMap(new Function<BleException, ObservableSource<?>>() {
                    @Override
                    public ObservableSource<?> apply(BleException e) throws Exception {
                        return Observable.error(e);
                    }
                });
    }

    private <T> Observable<T> withDisconnectionHandling(Output<T> output) {
        //noinspection unchecked
        return Observable.merge(
                output.valueRelay,
                (Observable<T>) output.errorRelay.flatMap(errorMapper)
        );
    }

    @NonNull
    @Override
    public BluetoothDevice getDevice() {
        return device;
    }

    public Completable setupNotifications(
        final BluetoothGattCharacteristic characteristic,
        final Single<Flowable<byte[]>> notifications,
        final boolean isIndication
    ) {
        final BluetoothGattDescriptor clientConfig = characteristic.getDescriptor(RxBleServer.CLIENT_CONFIG);
        if (clientConfig == null) {
            return Completable.error(new BleGattServerException(
                    device,
                    BleGattServerOperationType.NOTIFICATION_SENT,
                    "clientConfig is null"
            ));
        }

        return getOnDescriptorWriteRequest(clientConfig)
                .flatMap(new Function<ServerResponseTransaction, ObservableSource<Integer>>() {
                    @Override
                    public ObservableSource<Integer> apply(ServerResponseTransaction transaction) throws Exception {
                        return notifications
                                .toFlowable()
                                .flatMap(new Function<Flowable<byte[]>, Publisher<byte[]>>() {
                                    @Override
                                    public Publisher<byte[]> apply(
                                            @io.reactivex.annotations.NonNull Flowable<byte[]> flowable
                                    ) throws Exception {
                                        return flowable;
                                    }
                                })
                                .takeWhile(new Predicate<byte[]>() {
                            @Override
                            public boolean test(byte[] bytes) throws Exception {
                                return serverState.getNotifications(characteristic.getUuid());
                            }
                        }).toObservable().flatMap(new Function<byte[], ObservableSource<Integer>>() {
                            @Override
                            public ObservableSource<Integer> apply(byte[] bytes) throws Exception {
                                if (isIndication) {
                                    if (indicationResults.remove() != BluetoothGatt.GATT_SUCCESS) {
                                        return Observable.error(new BleGattServerException(
                                                device,
                                                BleGattServerOperationType.NOTIFICATION_SENT,
                                                "GATT_FAILURE"
                                        ));
                                    }
                                } else {
                                    if (notificationResults.remove() != BluetoothGatt.GATT_SUCCESS) {
                                        return Observable.error(new BleGattServerException(
                                                device,
                                                BleGattServerOperationType.NOTIFICATION_SENT,
                                                "GATT_FAILURE"
                                        ));
                                    }
                                }
                                return Observable.empty();
                            }
                        });
                    }
                }).ignoreElements();
    }

    @Override
    public Completable setupNotifications(BluetoothGattCharacteristic characteristic, Single<Flowable<byte[]>> notifications) {
        return setupNotifications(characteristic, notifications, false);
    }

    @Override
    public Completable setupIndication(BluetoothGattCharacteristic characteristic, Single<Flowable<byte[]>> indications) {
        return setupNotifications(characteristic, indications, true);
    }

    @Override
    public Observable<Integer> getOnMtuChanged() {
        return null;
    }

    @Override
    public Observable<ServerResponseTransaction> getOnCharacteristicReadRequest(
            BluetoothGattCharacteristic characteristic
    ) {
        return withDisconnectionHandling(readCharacteristicOutput)
                .map(new Function<GattServerTransaction<UUID>, ServerResponseTransaction>() {
                    @Override
                    public ServerResponseTransaction apply(GattServerTransaction<UUID> uuidGattServerTransaction) throws Exception {
                        return uuidGattServerTransaction.getTransaction();
                    }
                });
    }

    @Override
    public Observable<ServerResponseTransaction> getOnCharacteristicWriteRequest(
            BluetoothGattCharacteristic characteristic
    ) {
        return withDisconnectionHandling(writeCharacteristicOutput)
                .map(new Function<GattServerTransaction<UUID>, ServerResponseTransaction>() {
                    @Override
                    public ServerResponseTransaction apply(GattServerTransaction<UUID> uuidGattServerTransaction) throws Exception {
                        return uuidGattServerTransaction.getTransaction();
                    }
                });
    }

    @Override
    public Observable<ServerResponseTransaction> getOnDescriptorReadRequest(
            BluetoothGattDescriptor descriptor
    ) {
        return withDisconnectionHandling(readDescriptorOutput)
                .map(new Function<GattServerTransaction<BluetoothGattDescriptor>, ServerResponseTransaction>() {
                    @Override
                    public ServerResponseTransaction apply(GattServerTransaction<BluetoothGattDescriptor> transaction) throws Exception {
                        return transaction.getTransaction();
                    }
                });
    }

    @Override
    public Observable<ServerResponseTransaction> getOnDescriptorWriteRequest(
            BluetoothGattDescriptor descriptor
    ) {
        return withDisconnectionHandling(readDescriptorOutput)
                .map(new Function<GattServerTransaction<BluetoothGattDescriptor>, ServerResponseTransaction>() {
                    @Override
                    public ServerResponseTransaction apply(GattServerTransaction<BluetoothGattDescriptor> transaction) throws Exception {
                        return transaction.getTransaction();
                    }
                });
    }

    @Override
    public Single<Integer> indicationSingle(BluetoothGattCharacteristic characteristic, byte[] value) {
        if (indicationResults != null) {
            return Single.just(indicationResults.remove());
        } else {
            return Single.just(BluetoothGatt.GATT_SUCCESS);
        }
    }

    @Override
    public Single<Integer> notificationSingle(BluetoothGattCharacteristic characteristic, byte[] value) {
        if (notificationResults != null) {
            return Single.just(notificationResults.remove());
        } else {
            return Single.just(BluetoothGatt.GATT_SUCCESS);
        }
    }

    @Override
    public Observable<Integer> getOnNotification() {
        if (notificationResults != null) {
            return Observable.fromIterable(notificationResults);
        } else {
            return Observable.just(BluetoothGatt.GATT_SUCCESS).repeat();
        }
    }

    @Override
    public void disconnect() {
        disconnectionBehaviorRelay.accept(new BleDisconnectedException(device.getAddress(), 0));
    }

    @Override
    public <T> Observable<T> observeDisconnect() {
        //noinspection unchecked
        return (Observable<T>) disconnectionObservable;
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
        disconnectionBehaviorRelay.accept(exception);
    }

    @Override
    public void onDisconnectedException(BleDisconnectedException exception) {
        disconnectionBehaviorRelay.accept(exception);
    }

    @NonNull
    @Override
    public Output<byte[]> openLongWriteCharacteristicOutput(Integer requestid, BluetoothGattCharacteristic characteristic) {
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
                    .toSingle()
                    .subscribe(output.out);
            characteristicMultiIndex.put(requestid, output);
            characteristicMultiIndex.putMulti(characteristic, output);
        }
        return output;
    }

    @NonNull
    @Override
    public Output<byte[]> openLongWriteDescriptorOutput(Integer requestid, BluetoothGattDescriptor descriptor) {
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
            return output.out;
        }
        return Single.never();
    }

    @Override
    public Single<byte[]> closeLongWriteDescriptorOutput(Integer requestid) {
        LongWriteClosableOutput<byte[]> output = descriptorMultiIndex.get(requestid);
        if (output != null) {
            output.valueRelay.onComplete();
            characteristicMultiIndex.remove(requestid);
            return output.out;
        }
        return Single.never();
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
    public RxBleServerConnection getConnection() {
        return this;
    }

    @Override
    public void prepareDescriptorTransaction(
            final BluetoothGattDescriptor descriptor,
            int requestID,
            int offset,
            BluetoothDevice device,
            PublishRelay<GattServerTransaction<BluetoothGattDescriptor>> valueRelay,
            byte[] value
    ) {
        Disposable disposable = serverTransactionFactory.prepareCharacteristicTransaction(
                value,
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
                .subscribe(valueRelay);
        compositeDisposable.add(disposable);

    }

    @Override
    public void prepareCharacteristicTransaction(
            final BluetoothGattCharacteristic descriptor,
            int requestID,
            int offset,
            BluetoothDevice device,
            final PublishRelay<GattServerTransaction<UUID>> valueRelay,
            byte[] value) {
        Disposable disposable = serverTransactionFactory.prepareCharacteristicTransaction(
                value,
                requestID,
                offset,
                device
        )
                .map(new Function<ServerResponseTransaction, GattServerTransaction<UUID>>() {
                    @Override
                    public GattServerTransaction<UUID> apply(
                            ServerResponseTransaction serverResponseTransaction) throws Exception {
                        return new GattServerTransaction<>(descriptor.getUuid(), serverResponseTransaction);
                    }
                })
                .subscribe(valueRelay);
        compositeDisposable.add(disposable);
    }


    public static class Builder {
        private BluetoothDevice device;
        private Queue<Integer> notificationResults;
        private Queue<Integer> indicationResults;
        private RxBleServerState serverState;

        public Builder setBluetoothDevice(BluetoothDevice device) {
            this.device = device;
            return this;
        }

        public Builder setNotificationResults(Queue<Integer> notificationResults) {
            this.notificationResults = notificationResults;
            return this;
        }

        public Builder setIndicationResults(Queue<Integer> indicationResults) {
            this.indicationResults = indicationResults;
            return this;
        }

        public Builder setServerState(RxBleServerState serverState) {
            this.serverState = serverState;
            return this;
        }

        public RxBleServerConnectionMock build() {
            if (serverState == null) {
                throw new IllegalArgumentException("serverstate must be specified with setServerState");
            }

            if (device == null) {
                throw new IllegalArgumentException("BluetoothDevice must be specififed with setBluetoothDevice");
            }

            return new RxBleServerConnectionMock(
                    device,
                    serverState,
                    notificationResults,
                    indicationResults
            );
        }
    }
}
