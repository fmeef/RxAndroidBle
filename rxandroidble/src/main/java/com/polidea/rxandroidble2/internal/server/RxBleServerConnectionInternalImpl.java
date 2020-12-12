package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import androidx.annotation.NonNull;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleServer;
import com.polidea.rxandroidble2.RxBleServerConnection;
import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.ServerResponseTransaction;
import com.polidea.rxandroidble2.ServerTransactionFactory;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.exceptions.BleGattServerOperationType;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.operations.server.NotifyCharacteristicChangedOperation;
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProvider;
import com.polidea.rxandroidble2.internal.serialization.ServerConnectionOperationQueue;
import com.polidea.rxandroidble2.internal.util.GattServerTransaction;

import org.reactivestreams.Publisher;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.CompletableSource;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;

public class RxBleServerConnectionInternalImpl implements RxBleServerConnectionInternal, RxBleServerConnection {
    private final Scheduler connectionScheduler;
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
    private final BluetoothGattServerProvider gattServerProvider;
    private final RxBleServerState serverState;

    private final Function<BleException, Observable<?>> errorMapper = new Function<BleException, Observable<?>>() {
        @Override
        public Observable<?> apply(BleException bleGattException) {
            return Observable.error(bleGattException);
        }
    };

    @Inject
    public RxBleServerConnectionInternalImpl(
        @Named(ServerComponent.NamedSchedulers.BLUETOOTH_CONNECTION) Scheduler connectionScheduler,
        ServerConnectionOperationsProvider operationsProvider,
        ServerConnectionOperationQueue operationQueue,
        BluetoothDevice device,
        ServerDisconnectionRouter disconnectionRouter,
        ServerTransactionFactory serverTransactionFactory,
        BluetoothGattServerProvider serverProvider,
        RxBleServerState serverState

    ) {
        this.connectionScheduler = connectionScheduler;
        this.operationsProvider = operationsProvider;
        this.device = device;
        this.operationQueue = operationQueue;
        this.disconnectionRouter = disconnectionRouter;
        this.serverTransactionFactory = serverTransactionFactory;
        this.gattServerProvider = serverProvider;
        this.serverState = serverState;
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
                    .subscribeOn(connectionScheduler)
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
                    .subscribeOn(connectionScheduler)
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
            return output.out.delay(0, TimeUnit.SECONDS, connectionScheduler);
        }
        return Single.never();
    }

    @Override
    public Single<byte[]> closeLongWriteDescriptorOutput(Integer requestid) {
        LongWriteClosableOutput<byte[]> output = descriptorMultiIndex.get(requestid);
        if (output != null) {
            output.valueRelay.onComplete();
            characteristicMultiIndex.remove(requestid);
            return output.out.delay(0, TimeUnit.SECONDS, connectionScheduler);
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
    public Single<Integer> indicationSingle(UUID ch, byte[] value) {
        final BluetoothGattCharacteristic characteristic = serverState.getCharacteristic(ch);

        if (characteristic == null) {
            return Single.error(
                    new BleGattServerException(device, BleGattServerOperationType.NOTIFICATION_SENT, "characteristic not found")
            );
        }

        NotifyCharacteristicChangedOperation operation = operationsProvider.provideNotifyOperation(
                characteristic,
                value,
                true
        );
        return operationQueue.queue(operation).firstOrError();
    }

    @Override
    public Single<Integer> notificationSingle(UUID ch, byte[] value) {
        final BluetoothGattCharacteristic characteristic = serverState.getCharacteristic(ch);

        if (characteristic == null) {
            return Single.error(
                    new BleGattServerException(device, BleGattServerOperationType.NOTIFICATION_SENT, "characteristic not found")
            );
        }

        NotifyCharacteristicChangedOperation operation = operationsProvider.provideNotifyOperation(
                characteristic,
                value,
                false
        );
        return operationQueue.queue(operation).firstOrError();
    }

    @Override
    public Completable setupIndication(final UUID ch, final Flowable<byte[]> indications) {
        final BluetoothGattCharacteristic characteristic = serverState.getCharacteristic(ch);

        if (characteristic == null) {
            return Completable.error(
                    new BleGattServerException(device, BleGattServerOperationType.NOTIFICATION_SENT, "characteristic not found")
            );
        }
        return setupNotifications(characteristic, indications, true);
    }

    private Completable setupNotificationsDelay(
            final BluetoothGattDescriptor clientconfig,
            final BluetoothGattCharacteristic characteristic
    ) {
        if (serverState.getNotifications(characteristic.getUuid())) {
            return Completable.complete();
        }

        return withDisconnectionHandling(getWriteDescriptorOutput())
                .filter(new Predicate<GattServerTransaction<BluetoothGattDescriptor>>() {
                    @Override
                    public boolean test(GattServerTransaction<BluetoothGattDescriptor> transaction) throws Exception {
                        return transaction.getPayload().getUuid().compareTo(clientconfig.getUuid()) == 0
                                && transaction.getPayload().getCharacteristic().getUuid()
                                .compareTo(clientconfig.getCharacteristic().getUuid()) == 0;
                    }
                })
                .takeWhile(new Predicate<GattServerTransaction<BluetoothGattDescriptor>>() {
                    @Override
                    public boolean test(
                            @io.reactivex.annotations.NonNull GattServerTransaction<BluetoothGattDescriptor> transaction
                    ) throws Exception {
                        return Arrays.equals(transaction.getTransaction().getValue(), BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    }
                })
                .ignoreElements();
    }

    @Override
    public Completable setupNotifications(final UUID ch, final Flowable<byte[]> notifications) {
        final BluetoothGattCharacteristic characteristic = serverState.getCharacteristic(ch);

        if (characteristic == null) {
            return Completable.error(
                    new BleGattServerException(device, BleGattServerOperationType.NOTIFICATION_SENT, "characteristic not found")
            );
        }

        return setupNotifications(characteristic, notifications, false);
    }

    public Completable setupNotifications(
            final BluetoothGattCharacteristic characteristic,
            final Flowable<byte[]> notifications,
            final boolean isIndication
    ) {
        RxBleLog.d("setupNotifictions: " + characteristic.getUuid());
        final BluetoothGattDescriptor clientconfig = characteristic.getDescriptor(RxBleServer.CLIENT_CONFIG);
        if (clientconfig == null) {
            return Completable.error(new BleGattServerException(
                    device,
                    BleGattServerOperationType.NOTIFICATION_SENT,
                    "client config was null when setting up notifications"
            ));
        }
        return notifications
                .subscribeOn(connectionScheduler)
                .concatMap(new Function<byte[], Publisher<Integer>>() {
                            @Override
                            public Publisher<Integer> apply(@io.reactivex.annotations.NonNull byte[] bytes) throws Exception {
                                RxBleLog.d("processing bytes length: " + bytes.length);
                                NotifyCharacteristicChangedOperation operation
                                        = operationsProvider.provideNotifyOperation(
                                        characteristic,
                                        bytes,
                                        isIndication
                                );
                                return setupNotificationsDelay(clientconfig, characteristic)
                                         .andThen(operationQueue.queue(operation)).toFlowable(BackpressureStrategy.BUFFER);

                            }
                        })
                .reduce(new BiFunction<Integer, Integer, Integer>() {
                    @io.reactivex.annotations.NonNull
                    @Override
                    public Integer apply(
                            @io.reactivex.annotations.NonNull Integer integer,
                            @io.reactivex.annotations.NonNull Integer integer2
                    ) throws Exception {
                        if (integer != BluetoothGatt.GATT_SUCCESS) {
                            return integer;
                        } else if (integer2 != BluetoothGatt.GATT_SUCCESS) {
                            return integer2;
                        } else {
                            return BluetoothGatt.GATT_SUCCESS;
                        }
                    }
                })
                .flatMapCompletable(new Function<Integer, CompletableSource>() {
                    @Override
                    public CompletableSource apply(@io.reactivex.annotations.NonNull Integer integer) throws Exception {
                        if (integer == BluetoothGatt.GATT_SUCCESS) {
                            return Completable.complete();
                        } else {
                            return Completable.error(new BleGattServerException(
                                    integer,
                                    device,
                                    BleGattServerOperationType.NOTIFICATION_SENT,
                                    "failed to send notification"
                            ));
                        }
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
                .delay(0, TimeUnit.SECONDS, connectionScheduler);
    }

    @Override
    public void onGattConnectionStateException(BleGattServerException exception) {
        disconnectionRouter.onGattConnectionStateException(exception);
    }

    @Override
    public void onDisconnectedException(BleDisconnectedException exception) {
        disconnectionRouter.onDisconnectedException(exception);
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
    public Observable<ServerResponseTransaction> getOnCharacteristicReadRequest(final UUID characteristic) {
        return withDisconnectionHandling(getReadCharacteristicOutput())
                .filter(new Predicate<GattServerTransaction<UUID>>() {
                    @Override
                    public boolean test(GattServerTransaction<UUID> uuidGattServerTransaction) throws Exception {
                        return uuidGattServerTransaction.getPayload().compareTo(characteristic) == 0;
                    }
                })
                .map(new Function<GattServerTransaction<UUID>, ServerResponseTransaction>() {
                    @Override
                    public ServerResponseTransaction apply(GattServerTransaction<UUID> uuidGattServerTransaction) throws Exception {
                        return uuidGattServerTransaction.getTransaction();
                    }
                })
                .delay(0, TimeUnit.SECONDS, connectionScheduler);
    }

    @Override
    public Observable<ServerResponseTransaction> getOnCharacteristicWriteRequest(final UUID characteristic) {
        return withDisconnectionHandling(getWriteCharacteristicOutput())
                .filter(new Predicate<GattServerTransaction<UUID>>() {
                    @Override
                    public boolean test(GattServerTransaction<UUID> uuidGattServerTransaction) throws Exception {
                        return uuidGattServerTransaction.getPayload().compareTo(characteristic) == 0;
                    }
                })
                .map(new Function<GattServerTransaction<UUID>, ServerResponseTransaction>() {
                    @Override
                    public ServerResponseTransaction apply(GattServerTransaction<UUID> uuidGattServerTransaction) throws Exception {
                        return uuidGattServerTransaction.getTransaction();
                    }
                })
                .delay(0, TimeUnit.SECONDS, connectionScheduler);
    }

    @Override
    public Observable<ServerResponseTransaction> getOnDescriptorReadRequest(final UUID descriptor) {
        return withDisconnectionHandling(getReadDescriptorOutput())
                .filter(new Predicate<GattServerTransaction<BluetoothGattDescriptor>>() {
                    @Override
                    public boolean test(GattServerTransaction<BluetoothGattDescriptor> transaction) throws Exception {
                        return transaction.getPayload().getUuid().compareTo(descriptor) == 0
                                && transaction.getPayload().getCharacteristic().getUuid()
                                .compareTo(descriptor) == 0;
                    }
                })
                .map(new Function<GattServerTransaction<BluetoothGattDescriptor>, ServerResponseTransaction>() {
                    @Override
                    public ServerResponseTransaction apply(GattServerTransaction<BluetoothGattDescriptor> transaction) throws Exception {
                        return transaction.getTransaction();
                    }
                })
                .delay(0, TimeUnit.SECONDS, connectionScheduler);
    }

    @Override
    public Observable<ServerResponseTransaction> getOnDescriptorWriteRequest(
            final UUID descriptoruuid
    ) {
        final BluetoothGattDescriptor descriptor = serverState.getDescriptor(descriptoruuid);
        return withDisconnectionHandling(getWriteDescriptorOutput())
                .filter(new Predicate<GattServerTransaction<BluetoothGattDescriptor>>() {
                    @Override
                    public boolean test(GattServerTransaction<BluetoothGattDescriptor> transaction) throws Exception {
                        return transaction.getPayload().getUuid().compareTo(descriptor.getUuid()) == 0
                                && transaction.getPayload().getCharacteristic().getUuid()
                                .compareTo(descriptor.getCharacteristic().getUuid()) == 0;
                    }
                })
                .map(new Function<GattServerTransaction<BluetoothGattDescriptor>, ServerResponseTransaction>() {
                    @Override
                    public ServerResponseTransaction apply(GattServerTransaction<BluetoothGattDescriptor> transaction) throws Exception {
                        return transaction.getTransaction();
                    }
                })
                .delay(0, TimeUnit.SECONDS, connectionScheduler);
    }

    @Override
    public Observable<Integer> getOnNotification() {
        return getNotificationPublishRelay().valueRelay;
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
                .subscribeOn(connectionScheduler)
                .subscribe(valueRelay);
        compositeDisposable.add(disposable);
    }

    @Override
    public void prepareCharacteristicTransaction(
            final BluetoothGattCharacteristic characteristic,
            int requestID,
            int offset, BluetoothDevice device,
            PublishRelay<GattServerTransaction<UUID>> valueRelay,
            byte[] value
    ) {
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
                        return new GattServerTransaction<>(characteristic.getUuid(), serverResponseTransaction);
                    }
                })
                .subscribeOn(connectionScheduler)
                .subscribe(valueRelay);
        compositeDisposable.add(disposable);
    }

    @Override
    public Observable<Boolean> blindAck(int requestID, int status, byte[] value) {
        return operationQueue.queue(operationsProvider.provideReplyOperation(
                device,
                requestID,
                status,
                0,
                value
        ));
    }

    @Override
    public RxBleServerConnection getConnection() {
        return this;
    }
}
