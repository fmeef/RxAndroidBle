package com.polidea.rxandroidble2.internal.server;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.ClientComponent;
import com.polidea.rxandroidble2.NotificationSetupTransaction;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleServerConnection;
import com.polidea.rxandroidble2.ServerConfig;
import com.polidea.rxandroidble2.ServerConnectionScope;
import com.polidea.rxandroidble2.ServerResponseTransaction;
import com.polidea.rxandroidble2.ServerTransactionFactory;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.exceptions.BleGattServerOperationType;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.operations.server.NotifyCharacteristicChangedOperation;
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProvider;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueue;
import com.polidea.rxandroidble2.internal.util.GattServerTransaction;

import org.reactivestreams.Publisher;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.CompletableSource;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;

@ServerConnectionScope
public class RxBleServerConnectionImpl implements RxBleServerConnectionInternal, RxBleServerConnection, Disposable {
    private final Scheduler callbackScheduler;
    private final BluetoothManager bluetoothManager;
    private final AtomicReference<BluetoothGattServer> server = new AtomicReference<>(null);
    private final Context context;

    private final Scheduler connectionScheduler;
    private final RxBleServerState serverState;
    private final ServerConnectionOperationsProvider operationsProvider;
    private final ServerOperationQueue operationQueue;
    private final ServerDisconnectionRouter disconnectionRouter;
    private final MultiIndex<Integer, BluetoothGattCharacteristic, RxBleServerConnectionInternal.LongWriteClosableOutput<byte[]>>
            characteristicMultiIndex = new MultiIndexImpl<>();
    private final MultiIndex<Integer, BluetoothGattDescriptor, RxBleServerConnectionInternal.LongWriteClosableOutput<byte[]>>
            descriptorMultiIndex = new MultiIndexImpl<>();
    final ServerTransactionFactory serverTransactionFactory;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    private final Function<BleException, Observable<?>> errorMapper = new Function<BleException, Observable<?>>() {
        @Override
        public Observable<?> apply(BleException bleGattException) {
            return Observable.error(bleGattException);
        }
    };

    private final RxBleServerConnectionInternal.Output<GattServerTransaction<UUID>> readCharacteristicOutput =
            new RxBleServerConnectionInternal.Output<>();
    private final RxBleServerConnectionInternal.Output<GattServerTransaction<UUID>> writeCharacteristicOutput =
            new RxBleServerConnectionInternal.Output<>();
    private final RxBleServerConnectionInternal.Output<GattServerTransaction<BluetoothGattDescriptor>> readDescriptorOutput =
            new RxBleServerConnectionInternal.Output<>();
    private final RxBleServerConnectionInternal.Output<GattServerTransaction<BluetoothGattDescriptor>> writeDescriptorOutput =
            new RxBleServerConnectionInternal.Output<>();
    private final PublishRelay<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>> connectionStatePublishRelay =
            PublishRelay.create();
    private final RxBleServerConnectionInternal.Output<Integer> notificationPublishRelay =
            new RxBleServerConnectionInternal.Output<>();
    private final RxBleServerConnectionInternal.Output<Integer> changedMtuOutput =
            new RxBleServerConnectionInternal.Output<>();



    private boolean isDisconnecting(int newState) {
        return newState == BluetoothProfile.STATE_DISCONNECTED || newState == BluetoothProfile.STATE_DISCONNECTING;
    }

    private void acceptDevice(final BluetoothDevice device, int newState) {
        connectionStatePublishRelay.accept(new Pair<>(
                device, mapConnectionStateToRxBleConnectionStatus(newState)
        ));
    }

    public void registerService(BluetoothGattService service) {
        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
            if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0
                    || (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0) {
                RxBleLog.d("setting CLIENT_CONFIG for characteristic " + characteristic.getUuid());
                characteristic.addDescriptor(new BluetoothGattDescriptor(
                        RxBleClient.CLIENT_CONFIG,
                        BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ
                ));
            }

            serverState.addCharacteristic(characteristic.getUuid(), characteristic);
        }
        getBluetoothGattServer().addService(service);
    }



    @Nullable
    public BluetoothGattService getService(UUID uuid) {
        return getBluetoothGattServer().getService(uuid);
    }

    @Nullable
    public List<BluetoothGattService> getServiceList() {
        return getBluetoothGattServer().getServices();
    }

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothDevice device, final int status, final int newState) {
            super.onConnectionStateChange(device, status, newState);
            RxBleLog.d("gatt server onConnectionStateChange: " + device.getAddress() + " " + status + " " + newState);
            if (newState == BluetoothProfile.STATE_DISCONNECTED
                    || newState == BluetoothProfile.STATE_DISCONNECTING) {
                    onDisconnectedException(
                            new BleDisconnectedException(device.getAddress(), status)
                    );
                RxBleLog.e("device " + device.getAddress() + " disconnecting");
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                RxBleLog.e("GattServer state change failed %i", status);
                onGattConnectionStateException(
                        new BleGattServerException(
                                status,
                                BleGattServerOperationType.CONNECTION_STATE,
                                "onConnectionStateChange GATT_FAILURE"
                        )
                );
            }
            acceptDevice(device, newState);
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            //TODO:
        }

        @Override
        public void onCharacteristicReadRequest(final BluetoothDevice device,
                                                final int requestId,
                                                final int offset,
                                                final BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

            if (getReadCharacteristicOutput().hasObservers()) {

                prepareCharacteristicTransaction(
                        characteristic,
                        requestId,
                        offset,
                        device,
                        getReadCharacteristicOutput().valueRelay,
                        null
                );
            }
        }

        @Override
        public void onCharacteristicWriteRequest(final BluetoothDevice device,
                                                 final int requestId,
                                                 final BluetoothGattCharacteristic characteristic,
                                                 final boolean preparedWrite,
                                                 final boolean responseNeeded,
                                                 final int offset,
                                                 final byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            RxBleLog.d("onCharacteristicWriteRequest characteristic: " + characteristic.getUuid()
                    + " device: " + device.getAddress());

            if (preparedWrite) {
                RxBleLog.d("characteristic long write");
                RxBleServerConnectionInternal.Output<byte[]> longWriteOuput
                        = openLongWriteCharacteristicOutput(requestId, characteristic);
                longWriteOuput.valueRelay.accept(value);
            } else if (getWriteCharacteristicOutput().hasObservers()) {
                prepareCharacteristicTransaction(
                        characteristic,
                        requestId,
                        offset,
                        device,
                        getWriteCharacteristicOutput().valueRelay,
                        value
                );
            }
        }

        @Override
        public void onDescriptorReadRequest(final BluetoothDevice device,
                                            final int requestId,
                                            final int offset,
                                            final BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            RxBleLog.d("onDescriptorReadRequest: " + descriptor.getUuid());

            if (descriptor.getUuid().compareTo(RxBleClient.CLIENT_CONFIG) == 0) {
                blindAck(
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        serverState.getNotificationValue(descriptor.getCharacteristic().getUuid()),
                        device
                )
                        .subscribe();
            }

            if (getReadDescriptorOutput().hasObservers()) {
                prepareDescriptorTransaction(
                        descriptor,
                        requestId,
                        offset,
                        device,
                        getReadDescriptorOutput().valueRelay,
                        null
                );
            }
        }

        @Override
        public void onDescriptorWriteRequest(final BluetoothDevice device,
                                             final int requestId,
                                             final BluetoothGattDescriptor descriptor,
                                             final boolean preparedWrite,
                                             final boolean responseNeeded,
                                             final int offset,
                                             final byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            RxBleLog.d("onDescriptorWriteRequest: " + descriptor.getUuid());

            if (preparedWrite) {
                RxBleLog.d("onDescriptorWriteRequest: invoking preparedWrite");
                RxBleServerConnectionInternal.Output<byte[]> longWriteOutput
                        = openLongWriteDescriptorOutput(requestId, descriptor);
                longWriteOutput.valueRelay.accept(value); //TODO: offset?
            }  else {
                if (descriptor.getUuid().compareTo(RxBleClient.CLIENT_CONFIG) == 0) {
                    serverState.setNotifications(descriptor.getCharacteristic().getUuid(), value);
                    blindAck(requestId, BluetoothGatt.GATT_SUCCESS, null, device)
                            .subscribe();
                }

                if (getWriteDescriptorOutput().hasObservers()) {
                    prepareDescriptorTransaction(
                            descriptor,
                            requestId,
                            offset,
                            device,
                            getWriteDescriptorOutput().valueRelay,
                            value
                    );
                }
            }
        }

        @Override
        public void onExecuteWrite(final BluetoothDevice device, final int requestId, final boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            if (execute) {
                closeLongWriteCharacteristicOutput(requestId);

                resetCharacteristicMap();
                resetDescriptorMap();
            }
        }

        @Override
        public void onNotificationSent(final BluetoothDevice device, final int status) {
            super.onNotificationSent(device, status);

            if (getNotificationPublishRelay().hasObservers()) {
                RxBleLog.d("onNotificationSent: " + device.getAddress() + " " + status);
                getNotificationPublishRelay().valueRelay.accept(
                        status
                );
            }
        }

        @Override
        public void onMtuChanged(final BluetoothDevice device, final int mtu) {
            super.onMtuChanged(device, mtu);

            if (getChangedMtuOutput().hasObservers()) {
                getChangedMtuOutput().valueRelay.accept(mtu);
            }
        }

        @Override
        public void onPhyUpdate(BluetoothDevice device, int txPhy, int rxPhy, int status) {
            super.onPhyUpdate(device, txPhy, rxPhy, status);
            //TODO: handle phy change
        }

        @Override
        public void onPhyRead(BluetoothDevice device, int txPhy, int rxPhy, int status) {
            super.onPhyRead(device, txPhy, rxPhy, status);
            //TODO: handle phy read
        }
    };

    @Inject
    public RxBleServerConnectionImpl(
            @Named(ClientComponent.NamedSchedulers.BLUETOOTH_INTERACTION) Scheduler connectionScheduler,
            @Named(ClientComponent.NamedSchedulers.BLUETOOTH_CALLBACKS) Scheduler callbackScheduler,
            ServerConnectionOperationsProvider operationsProvider,
            ServerOperationQueue operationQueue,
            BluetoothManager bluetoothManager,
            ServerDisconnectionRouter disconnectionRouter,
            ServerTransactionFactory serverTransactionFactory,
            ServerConfig config,
            Context context,
            RxBleServerState serverState
    ) {
        this.connectionScheduler = connectionScheduler;
        this.operationsProvider = operationsProvider;
        this.operationQueue = operationQueue;
        this.bluetoothManager = bluetoothManager;

        this.disconnectionRouter = disconnectionRouter;
        this.serverTransactionFactory = serverTransactionFactory;
        this.context = context;
        this.callbackScheduler = callbackScheduler;
        this.serverState = serverState;
        initializeServer(config);
    }

    public BluetoothGattServer getBluetoothGattServer() {
        BluetoothGattServer check = server.get();
        if (check == null) {
            BluetoothGattServer newserver = bluetoothManager.openGattServer(context, gattServerCallback);
            server.compareAndSet(null, newserver);
            return newserver;
        } else {
            return check;
        }
    }

    static RxBleConnection.RxBleConnectionState mapConnectionStateToRxBleConnectionStatus(int newState) {

        switch (newState) {
            case BluetoothProfile.STATE_CONNECTING:
                return RxBleConnection.RxBleConnectionState.CONNECTING;
            case BluetoothProfile.STATE_CONNECTED:
                return RxBleConnection.RxBleConnectionState.CONNECTED;
            case BluetoothProfile.STATE_DISCONNECTING:
                return RxBleConnection.RxBleConnectionState.DISCONNECTING;
            default:
                return RxBleConnection.RxBleConnectionState.DISCONNECTED;
        }

    }

    private static boolean isException(int status) {
        return status != BluetoothGatt.GATT_SUCCESS;
    }

    private static boolean propagateStatusError(RxBleServerConnectionInternal.Output<?> output, BleGattServerException exception) {
        output.errorRelay.accept(exception);
        return true;
    }

    /**
     * @return Observable that emits RxBleConnectionState that matches BluetoothGatt's state.
     * Does NOT emit errors even if status != GATT_SUCCESS.
     */
    @Override
    public Observable<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>> getOnConnectionStateChange() {
        return connectionStatePublishRelay.delay(0, TimeUnit.SECONDS, callbackScheduler);
    }


    private boolean initializeServer(ServerConfig config) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int phyval = 0;
            for (ServerConfig.BluetoothPhy phy : config.getPhySet()) {
                switch (phy) {
                    case PHY_LE_1M:
                        phyval |= BluetoothDevice.PHY_LE_1M_MASK;
                        break;
                    case PHY_LE_2M:
                        phyval |= BluetoothDevice.PHY_LE_2M_MASK;
                        break;
                    case PHY_LE_CODED:
                        phyval |= BluetoothDevice.PHY_LE_CODED_MASK;
                        break;
                    default:
                        // here to please linter
                        Log.e("debug", "we should never reach here");
                        break;
                }
            }
        }

        for (Map.Entry<UUID, BluetoothGattService> entry : config.getServices().entrySet()) {
            registerService(entry.getValue());
        }

        return true;
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
        return Single.just(requestid)
                .flatMap(new Function<Integer, SingleSource<? extends byte[]>>() {
                    @Override
                    public SingleSource<? extends byte[]> apply(@io.reactivex.annotations.NonNull Integer integer) throws Exception {
                        LongWriteClosableOutput<byte[]> output = characteristicMultiIndex.get(integer);
                        if (output != null) {
                            output.valueRelay.onComplete();
                            characteristicMultiIndex.remove(integer);
                            return output.out.delay(0, TimeUnit.SECONDS, connectionScheduler);
                        }
                        return Single.never();
                    }
                });
    }

    @Override
    public Single<NotificationSetupTransaction> awaitNotifications(final UUID characteristic) {
        return Single.defer(new Callable<SingleSource<? extends NotificationSetupTransaction>>() {
            @Override
            public SingleSource<? extends NotificationSetupTransaction> call() throws Exception {
                final BluetoothGattCharacteristic ch = serverState.getCharacteristic(characteristic);
                final BluetoothGattDescriptor clientconfig = ch.getDescriptor(RxBleClient.CLIENT_CONFIG);

                if (clientconfig == null) {
                    return Single.error(new BleGattServerException(
                            BleGattServerOperationType.NOTIFICATION_SENT,
                            "client config was null when setting up notifications"
                    ));
                } else {
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
                                        @io.reactivex.annotations.NonNull GattServerTransaction<BluetoothGattDescriptor> trans
                                ) throws Exception {
                                    return Arrays.equals(
                                            trans.getTransaction().getValue(),
                                            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                                    );
                                }
                            })
                            .firstOrError()
                            .flatMap(
                                    new Function<
                                            GattServerTransaction<BluetoothGattDescriptor>,
                                            SingleSource<? extends NotificationSetupTransaction
                                                    >
                                            >() {
                                @Override
                                public SingleSource<? extends NotificationSetupTransaction> apply(
                                        @NonNull GattServerTransaction<BluetoothGattDescriptor> trans
                                ) throws Exception {
                                    return serverTransactionFactory.prepareNotificationSetupTransaction(
                                            trans.getTransaction().getRemoteDevice()
                                    );
                                }
                            });
                }

            }
        });
    }

    @Override
    public Single<byte[]> closeLongWriteDescriptorOutput(Integer requestid) {
        return Single.just(requestid)
                .flatMap(new Function<Integer, SingleSource<? extends byte[]>>() {
                    @Override
                    public SingleSource<? extends byte[]> apply(@io.reactivex.annotations.NonNull Integer integer) throws Exception {
                        LongWriteClosableOutput<byte[]> output = descriptorMultiIndex.get(integer);
                        if (output != null) {
                            output.valueRelay.onComplete();
                            characteristicMultiIndex.remove(integer);
                            return output.out.delay(0, TimeUnit.SECONDS, connectionScheduler);
                        }
                        return Single.never();
                    }
                });
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
    public Single<Integer> indicationSingle(final UUID ch, final byte[] value, final BluetoothDevice device) {
        return Single.fromCallable(new Callable<Single<Integer>>() {
            @Override
            public Single<Integer> call() throws Exception {
                final BluetoothGattCharacteristic characteristic = serverState.getCharacteristic(ch);

                if (characteristic == null) {
                    return Single.error(
                            new BleGattServerException(BleGattServerOperationType.NOTIFICATION_SENT, "characteristic not found")
                    );
                }

                NotifyCharacteristicChangedOperation operation = operationsProvider.provideNotifyOperation(
                        characteristic,
                        value,
                        true,
                        device
                );
                return operationQueue.queue(operation).firstOrError();
            }
        }).flatMap(new Function<Single<Integer>, SingleSource<? extends Integer>>() {
            @Override
            public SingleSource<? extends Integer> apply(@io.reactivex.annotations.NonNull Single<Integer> integerSingle) throws Exception {
                return integerSingle;
            }
        });
    }

    @Override
    public Single<Integer> notificationSingle(final UUID ch, final byte[] value, final BluetoothDevice device) {
        return Single.fromCallable(new Callable<Single<Integer>>() {
            @Override
            public Single<Integer> call() throws Exception {
                final BluetoothGattCharacteristic characteristic = serverState.getCharacteristic(ch);

                if (characteristic == null) {
                    return Single.error(
                            new BleGattServerException(BleGattServerOperationType.NOTIFICATION_SENT, "characteristic not found")
                    );
                }

                NotifyCharacteristicChangedOperation operation = operationsProvider.provideNotifyOperation(
                        characteristic,
                        value,
                        false,
                        device
                );
                return operationQueue.queue(operation).firstOrError();
            }
        }).flatMap(new Function<Single<Integer>, SingleSource<? extends Integer>>() {
            @Override
            public SingleSource<? extends Integer> apply(@io.reactivex.annotations.NonNull Single<Integer> integerSingle) throws Exception {
                return integerSingle;
            }
        });
    }

    @Override
    public Completable setupIndication(final UUID ch, final Flowable<byte[]> indications, final BluetoothDevice device) {
        return Single.fromCallable(new Callable<Completable>() {

            @Override
            public Completable call() throws Exception {
                final BluetoothGattCharacteristic characteristic = serverState.getCharacteristic(ch);

                if (characteristic == null) {
                    throw new BleGattServerException(BleGattServerOperationType.NOTIFICATION_SENT, "characteristic not found");
                }
                return setupNotifications(characteristic, indications, true, device);
            }
        }).flatMapCompletable(new Function<Completable, CompletableSource>() {
            @Override
            public CompletableSource apply(@io.reactivex.annotations.NonNull Completable completable) throws Exception {
                return completable;
            }
        });
    }

    private Completable setupNotificationsDelay(
            final BluetoothGattDescriptor clientconfig,
            final BluetoothGattCharacteristic characteristic,
            final boolean isIndication
    ) {
        return Single.fromCallable(new Callable<Completable>() {

            @Override
            public Completable call() throws Exception {
                if (isIndication) {
                    if (serverState.getIndications(characteristic.getUuid())) {
                        RxBleLog.d("immediate start indication");
                        return Completable.complete();
                    }
                } else {
                    if (serverState.getNotifications(characteristic.getUuid())) {
                        RxBleLog.d("immediate start notification");
                        return Completable.complete();
                    }
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
                                    @io.reactivex.annotations.NonNull GattServerTransaction<BluetoothGattDescriptor> trans
                            ) throws Exception {
                                return Arrays.equals(trans.getTransaction().getValue(), BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                            }
                        })
                        .ignoreElements();
            }
        }).flatMapCompletable(new Function<Completable, CompletableSource>() {
            @Override
            public CompletableSource apply(@io.reactivex.annotations.NonNull Completable completable) throws Exception {
                return completable;
            }
        });

    }

    @Override
    public Completable setupNotifications(final UUID ch, final Flowable<byte[]> notifications, final BluetoothDevice device) {
        return Single.fromCallable(new Callable<Completable>() {
            @Override
            public Completable call() throws Exception {
                final BluetoothGattCharacteristic characteristic = serverState.getCharacteristic(ch);

                if (characteristic == null) {
                    return Completable.error(
                            new BleGattServerException(BleGattServerOperationType.NOTIFICATION_SENT, "characteristic not found")
                    );
                }

                return setupNotifications(characteristic, notifications, false, device);
            }
        }).flatMapCompletable(new Function<Completable, CompletableSource>() {
            @Override
            public CompletableSource apply(@io.reactivex.annotations.NonNull Completable completable) throws Exception {
                return completable;
            }
        });
    }

    public Completable setupNotifications(
            final BluetoothGattCharacteristic characteristic,
            final Flowable<byte[]> notifications,
            final boolean isIndication,
            final BluetoothDevice device
    ) {
        return Single.fromCallable(new Callable<Completable>() {
            @Override
            public Completable call() throws Exception {
                RxBleLog.d("setupNotifictions: " + characteristic.getUuid());
                final BluetoothGattDescriptor clientconfig = characteristic.getDescriptor(RxBleClient.CLIENT_CONFIG);
                if (clientconfig == null) {
                    return Completable.error(new BleGattServerException(
                            BleGattServerOperationType.NOTIFICATION_SENT,
                            "client config was null when setting up notifications"
                    ));
                }
                return notifications
                        .subscribeOn(connectionScheduler)
                        .delay(new Function<byte[], Publisher<byte[]>>() {
                            @Override
                            public Publisher<byte[]> apply(@io.reactivex.annotations.NonNull byte[] bytes) throws Exception {
                                return setupNotificationsDelay(clientconfig, characteristic, isIndication)
                                        .toFlowable();
                            }
                        })
                        .concatMap(new Function<byte[], Publisher<Integer>>() {
                            @Override
                            public Publisher<Integer> apply(@io.reactivex.annotations.NonNull final byte[] bytes) throws Exception {
                                RxBleLog.d("processing bytes length: " + bytes.length);
                                final NotifyCharacteristicChangedOperation operation
                                        = operationsProvider.provideNotifyOperation(
                                        characteristic,
                                        bytes,
                                        isIndication,
                                        device
                                );
                                RxBleLog.d("queueing notification/indication");
                                return operationQueue.queue(operation).toFlowable(BackpressureStrategy.BUFFER);
                            }
                        })
                        .flatMap(new Function<Integer, Publisher<Integer>>() {
                            @Override
                            public Publisher<Integer> apply(@io.reactivex.annotations.NonNull Integer integer) throws Exception {
                                RxBleLog.d("notification result: " + integer);
                                if (integer != BluetoothGatt.GATT_SUCCESS) {
                                    return Flowable.error(new BleGattServerException(
                                            BleGattServerOperationType.NOTIFICATION_SENT,
                                            "notification operation did not return GATT_SUCCESS"
                                    ));
                                } else {
                                    return Flowable.just(integer);
                                }
                            }
                        })
                        .ignoreElements()
                        .doOnComplete(new Action() {
                            @Override
                            public void run() throws Exception {
                                RxBleLog.d("notifications completed!");
                            }
                        });
            }
        }).flatMapCompletable(new Function<Completable, CompletableSource>() {
            @Override
            public CompletableSource apply(@io.reactivex.annotations.NonNull Completable completable) throws Exception {
                return completable;
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
    public Observable<ServerResponseTransaction> getOnDescriptorReadRequest(
            final UUID characteristic,
            final UUID descriptor
    ) {
        return withDisconnectionHandling(getReadDescriptorOutput())
                .filter(new Predicate<GattServerTransaction<BluetoothGattDescriptor>>() {
                    @Override
                    public boolean test(GattServerTransaction<BluetoothGattDescriptor> transaction) throws Exception {
                        return transaction.getPayload().getUuid().compareTo(descriptor) == 0
                                && transaction.getPayload().getCharacteristic().getUuid()
                                .compareTo(characteristic) == 0;
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
            final UUID characteristicuuid,
            final UUID descriptoruuid
    ) {
        return withDisconnectionHandling(getWriteDescriptorOutput())
                .filter(new Predicate<GattServerTransaction<BluetoothGattDescriptor>>() {
                    @Override
                    public boolean test(GattServerTransaction<BluetoothGattDescriptor> transaction) throws Exception {
                        return transaction.getPayload().getUuid().compareTo(descriptoruuid) == 0
                                && transaction.getPayload().getCharacteristic().getUuid()
                                .compareTo(characteristicuuid) == 0;
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
        return notificationPublishRelay.valueRelay;
    }

    @Override
    public void disconnect(final BluetoothDevice device) {
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
    public Observable<Boolean> blindAck(int requestID, int status, byte[] value, final BluetoothDevice device) {
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

    @Override
    public void dispose() {
        connectionScheduler.shutdown();
        compositeDisposable.dispose();
    }

    @Override
    public boolean isDisposed() {
        return compositeDisposable.isDisposed();
    }
}
