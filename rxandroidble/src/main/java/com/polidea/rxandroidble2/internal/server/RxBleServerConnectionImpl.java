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

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.ClientComponent;
import com.polidea.rxandroidble2.NotificationSetupTransaction;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.RxBleServerConnection;
import com.polidea.rxandroidble2.ServerConfig;
import com.polidea.rxandroidble2.ServerConnectionScope;
import com.polidea.rxandroidble2.ServerResponseTransaction;
import com.polidea.rxandroidble2.ServerTransactionFactory;
import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.exceptions.BleGattServerOperationType;
import com.polidea.rxandroidble2.internal.RxBleDeviceProvider;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.operations.server.NotifyCharacteristicChangedOperation;
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProvider;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueue;
import com.polidea.rxandroidble2.internal.util.GattServerTransaction;

import org.reactivestreams.Publisher;

import java.util.Arrays;
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
    private final RxBleServerState serverState;
    private final ServerConnectionOperationsProvider operationsProvider;
    private final ServerOperationQueue operationQueue;
    private final RxBleDeviceProvider deviceProvider;
    private final MultiIndex<Integer, BluetoothGattCharacteristic, RxBleServerConnectionInternal.LongWriteClosableOutput<byte[]>>
            characteristicMultiIndex = new MultiIndexImpl<>();
    private final MultiIndex<Integer, BluetoothGattDescriptor, RxBleServerConnectionInternal.LongWriteClosableOutput<byte[]>>
            descriptorMultiIndex = new MultiIndexImpl<>();
    final ServerTransactionFactory serverTransactionFactory;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    private final Function<BleException, Observable<?>> errorMapper = new Function<BleException, Observable<?>>() {
        @Override
        public Observable<?> apply(@NonNull BleException bleGattException) {
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
    private final PublishRelay<Pair<RxBleDevice, RxBleConnection.RxBleConnectionState>> connectionStatePublishRelay =
            PublishRelay.create();
    private final RxBleServerConnectionInternal.Output<Integer> notificationPublishRelay =
            new RxBleServerConnectionInternal.Output<>();
    private final RxBleServerConnectionInternal.Output<Integer> changedMtuOutput =
            new RxBleServerConnectionInternal.Output<>();

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

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothDevice device, final int status, final int newState) {
            super.onConnectionStateChange(device, status, newState);
            RxBleLog.d("gatt server onConnectionStateChange: " + device.getAddress() + " " + status + " " + newState);
            if (newState == BluetoothProfile.STATE_DISCONNECTED
                    || newState == BluetoothProfile.STATE_DISCONNECTING) {
                //TODO:
                RxBleLog.e("device " + device.getAddress() + " disconnecting");
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                //TODO:
                RxBleLog.e("GattServer state change failed %i", status);
            }
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

            final RxBleDevice rxBleDevice = deviceProvider.getBleDevice(device.getAddress());

            if (getReadCharacteristicOutput().hasObservers()) {

                prepareCharacteristicTransaction(
                        characteristic,
                        requestId,
                        offset,
                        rxBleDevice,
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
            RxBleLog.v("onCharacteristicWriteRequest characteristic: " + characteristic.getUuid()
                    + " device: " + device.getAddress());

            final RxBleDevice rxBleDevice = deviceProvider.getBleDevice(device.getAddress());

            if (preparedWrite) {
                RxBleLog.v("characteristic long write");
                RxBleServerConnectionInternal.Output<byte[]> longWriteOuput
                        = openLongWriteCharacteristicOutput(requestId, characteristic);
                longWriteOuput.valueRelay.accept(value);
            } else if (getWriteCharacteristicOutput().hasObservers()) {
                prepareCharacteristicTransaction(
                        characteristic,
                        requestId,
                        offset,
                        rxBleDevice,
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
            RxBleLog.v("onDescriptorReadRequest: " + descriptor.getUuid());

            final RxBleDevice rxBleDevice = deviceProvider.getBleDevice(device.getAddress());

            if (descriptor.getUuid().compareTo(RxBleClient.CLIENT_CONFIG) == 0) {
                blindAck(
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        serverState.getNotificationValue(descriptor.getCharacteristic().getUuid()),
                        rxBleDevice
                )
                        .subscribe();
            }

            if (getReadDescriptorOutput().hasObservers()) {
                prepareDescriptorTransaction(
                        descriptor,
                        requestId,
                        offset,
                        rxBleDevice,
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
            RxBleLog.v("onDescriptorWriteRequest: " + descriptor.getUuid());

            final RxBleDevice rxBleDevice = deviceProvider.getBleDevice(device.getAddress());
            if (preparedWrite) {
                RxBleLog.v("onDescriptorWriteRequest: invoking preparedWrite");
                RxBleServerConnectionInternal.Output<byte[]> longWriteOutput
                        = openLongWriteDescriptorOutput(requestId, descriptor);
                longWriteOutput.valueRelay.accept(value); //TODO: offset?
            }  else {
                if (descriptor.getUuid().compareTo(RxBleClient.CLIENT_CONFIG) == 0) {
                    serverState.setNotifications(descriptor.getCharacteristic().getUuid(), value);
                    blindAck(requestId, BluetoothGatt.GATT_SUCCESS, null, rxBleDevice)
                            .subscribe();
                }

                if (writeDescriptorOutput.hasObservers()) {
                    prepareDescriptorTransaction(
                            descriptor,
                            requestId,
                            offset,
                            rxBleDevice,
                            writeDescriptorOutput.valueRelay,
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
                RxBleLog.v("onNotificationSent: " + device.getAddress() + " " + status);
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
            @Named(ClientComponent.NamedSchedulers.SERVER_CALLBACKS) Scheduler callbackScheduler,
            ServerConnectionOperationsProvider operationsProvider,
            ServerOperationQueue operationQueue,
            BluetoothManager bluetoothManager,
            ServerTransactionFactory serverTransactionFactory,
            ServerConfig config,
            Context context,
            RxBleServerState serverState,
            RxBleDeviceProvider deviceProvider
    ) {
        this.operationsProvider = operationsProvider;
        this.operationQueue = operationQueue;
        this.bluetoothManager = bluetoothManager;

        this.serverTransactionFactory = serverTransactionFactory;
        this.context = context;
        this.callbackScheduler = callbackScheduler;
        this.serverState = serverState;
        this.deviceProvider = deviceProvider;
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

    /**
     * @return Observable that emits RxBleConnectionState that matches BluetoothGatt's state.
     * Does NOT emit errors even if status != GATT_SUCCESS.
     */
    @Override
    public Observable<Pair<RxBleDevice, RxBleConnection.RxBleConnectionState>> getOnConnectionStateChange() {
        return connectionStatePublishRelay.delay(0, TimeUnit.SECONDS, callbackScheduler);
    }


    private void initializeServer(ServerConfig config) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            for (ServerConfig.BluetoothPhy phy : config.getPhySet()) {
                switch (phy) {
                    case PHY_LE_1M:
                    case PHY_LE_2M:
                    case PHY_LE_CODED:
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
                        @NonNull
                        @Override
                        public byte[] apply(@NonNull byte[] first, @NonNull byte[] second) {
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
    public LongWriteClosableOutput<byte[]> openLongWriteDescriptorOutput(Integer requestid, BluetoothGattDescriptor descriptor) {
        LongWriteClosableOutput<byte[]> output = descriptorMultiIndex.get(requestid);
        if (output == null) {
            output = new LongWriteClosableOutput<>();
            output.valueRelay
                    .reduce(new BiFunction<byte[], byte[], byte[]>() {
                        @NonNull
                        @Override
                        public byte[] apply(@NonNull byte[] first, @NonNull byte[] second) {
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
        return Single.just(requestid)
                .flatMap(new Function<Integer, SingleSource<? extends byte[]>>() {
                    @Override
                    public SingleSource<? extends byte[]> apply(@io.reactivex.annotations.NonNull Integer integer) {
                        LongWriteClosableOutput<byte[]> output = characteristicMultiIndex.get(integer);
                        if (output != null) {
                            output.valueRelay.onComplete();
                            characteristicMultiIndex.remove(integer);
                            return output.out.delay(0, TimeUnit.SECONDS, callbackScheduler);
                        }
                        return Single.never();
                    }
                });
    }

    @Override
    public Single<NotificationSetupTransaction> awaitNotifications(final UUID characteristic) {
        return Single.defer(new Callable<SingleSource<? extends NotificationSetupTransaction>>() {
            @Override
            public SingleSource<? extends NotificationSetupTransaction> call() {
                final BluetoothGattCharacteristic ch = serverState.getCharacteristic(characteristic);
                final BluetoothGattDescriptor clientconfig = ch.getDescriptor(RxBleClient.CLIENT_CONFIG);

                if (clientconfig == null) {
                    return Single.error(new BleGattServerException(
                            BleGattServerOperationType.NOTIFICATION_SENT,
                            "client config was null when setting up notifications"
                    ));
                } else {
                    return withDisconnectionHandling(writeDescriptorOutput)
                            .filter(new Predicate<GattServerTransaction<BluetoothGattDescriptor>>() {
                                @Override
                                public boolean test(
                                        @NonNull GattServerTransaction<BluetoothGattDescriptor> transaction
                                ) {
                                    return transaction.getPayload().getUuid().compareTo(RxBleClient.CLIENT_CONFIG) == 0
                                            && transaction.getPayload().getCharacteristic().getUuid()
                                            .compareTo(characteristic) == 0;
                                }
                            })
                            .takeUntil(new Predicate<GattServerTransaction<BluetoothGattDescriptor>>() {
                                @Override
                                public boolean test(
                                        @io.reactivex.annotations.NonNull GattServerTransaction<BluetoothGattDescriptor> trans
                                ) {
                                    return !Arrays.equals(
                                            trans.getTransaction().getValue(),
                                            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                                    );
                                    }
                            })
                            .firstOrError()
                            .map(new Function<GattServerTransaction<BluetoothGattDescriptor>, NotificationSetupTransaction>() {
                                @Override
                                public NotificationSetupTransaction apply(
                                        @NonNull GattServerTransaction<BluetoothGattDescriptor> trans
                                ) {
                                    return serverTransactionFactory.prepareNotificationSetupTransaction(
                                            trans.getTransaction().getRemoteDevice(),
                                            characteristic
                                    );
                                }
                            })
                            .delay(0, TimeUnit.SECONDS, callbackScheduler);
                    }

            }
        });
    }

    @Override
    public Single<byte[]> closeLongWriteDescriptorOutput(Integer requestid) {
        return Single.just(requestid)
                .flatMap(new Function<Integer, SingleSource<? extends byte[]>>() {
                    @Override
                    public SingleSource<? extends byte[]> apply(@io.reactivex.annotations.NonNull Integer integer) {
                        LongWriteClosableOutput<byte[]> output = descriptorMultiIndex.get(integer);
                        if (output != null) {
                            output.valueRelay.onComplete();
                            characteristicMultiIndex.remove(integer);
                            return output.out.delay(0, TimeUnit.SECONDS, callbackScheduler);
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
    public Completable setupIndication(final UUID ch, final Flowable<byte[]> indications, final RxBleDevice device) {
        return Single.fromCallable(new Callable<Completable>() {

            @Override
            public Completable call() {
                final BluetoothGattCharacteristic characteristic = serverState.getCharacteristic(ch);

                if (characteristic == null) {
                    throw new BleGattServerException(BleGattServerOperationType.NOTIFICATION_SENT, "characteristic not found");
                }
                return setupNotifications(characteristic, indications, true, device);
            }
        }).flatMapCompletable(new Function<Completable, CompletableSource>() {
            @Override
            public CompletableSource apply(@io.reactivex.annotations.NonNull Completable completable) {
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
            public Completable call() {
                if (isIndication) {
                    if (serverState.getIndications(characteristic.getUuid())) {
                        RxBleLog.v("immediate start indication");
                        return Completable.complete();
                    }
                } else {
                    if (serverState.getNotifications(characteristic.getUuid())) {
                        RxBleLog.v("immediate start notification");
                        return Completable.complete();
                    }
                }

                return withDisconnectionHandling(getWriteDescriptorOutput())
                        .filter(new Predicate<GattServerTransaction<BluetoothGattDescriptor>>() {
                            @Override
                            public boolean test(@NonNull GattServerTransaction<BluetoothGattDescriptor> transaction) {
                                return transaction.getPayload().getUuid().compareTo(clientconfig.getUuid()) == 0
                                        && transaction.getPayload().getCharacteristic().getUuid()
                                        .compareTo(clientconfig.getCharacteristic().getUuid()) == 0;
                            }
                        })
                        .takeWhile(new Predicate<GattServerTransaction<BluetoothGattDescriptor>>() {
                            @Override
                            public boolean test(
                                    @io.reactivex.annotations.NonNull GattServerTransaction<BluetoothGattDescriptor> trans
                            ) {
                                return Arrays.equals(trans.getTransaction().getValue(), BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                            }
                        })
                        .ignoreElements();
            }
        }).flatMapCompletable(new Function<Completable, CompletableSource>() {
            @Override
            public CompletableSource apply(@io.reactivex.annotations.NonNull Completable completable) {
                return completable;
            }
        });

    }

    @Override
    public Completable setupNotifications(final UUID ch, final Flowable<byte[]> notifications, final RxBleDevice device) {
        return Single.fromCallable(new Callable<Completable>() {
            @Override
            public Completable call() {
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
            public CompletableSource apply(@io.reactivex.annotations.NonNull Completable completable) {
                return completable;
            }
        });
    }

    public Completable setupNotifications(
            final BluetoothGattCharacteristic characteristic,
            final Flowable<byte[]> notifications,
            final boolean isIndication,
            final RxBleDevice device
    ) {
        return Single.fromCallable(new Callable<Completable>() {
            @Override
            public Completable call() {
                RxBleLog.v("setupNotifictions: " + characteristic.getUuid());
                final BluetoothGattDescriptor clientconfig = characteristic.getDescriptor(RxBleClient.CLIENT_CONFIG);
                if (clientconfig == null) {
                    return Completable.error(new BleGattServerException(
                            BleGattServerOperationType.NOTIFICATION_SENT,
                            "client config was null when setting up notifications"
                    ));
                }
                return notifications
                        .delay(new Function<byte[], Publisher<byte[]>>() {
                            @Override
                            public Publisher<byte[]> apply(@io.reactivex.annotations.NonNull byte[] bytes) {
                                return setupNotificationsDelay(clientconfig, characteristic, isIndication)
                                        .toFlowable();
                            }
                        })
                        .concatMap(new Function<byte[], Publisher<Integer>>() {
                            @Override
                            public Publisher<Integer> apply(@io.reactivex.annotations.NonNull final byte[] bytes) {
                                RxBleLog.v("processing bytes length: " + bytes.length);
                                final NotifyCharacteristicChangedOperation operation
                                        = operationsProvider.provideNotifyOperation(
                                        characteristic,
                                        bytes,
                                        isIndication,
                                        device
                                );
                                RxBleLog.v("queueing notification/indication");
                                return operationQueue.queue(operation).toFlowable(BackpressureStrategy.BUFFER);
                            }
                        })
                        .flatMap(new Function<Integer, Publisher<Integer>>() {
                            @Override
                            public Publisher<Integer> apply(@io.reactivex.annotations.NonNull Integer integer) {
                                RxBleLog.v("notification result: " + integer);
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
                            public void run() {
                                RxBleLog.v("notifications completed!");
                            }
                        });
            }
        }).flatMapCompletable(new Function<Completable, CompletableSource>() {
            @Override
            public CompletableSource apply(@io.reactivex.annotations.NonNull Completable completable) {
                return completable;
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

    @Override
    public Observable<Integer> getOnMtuChanged() {
        return withDisconnectionHandling(getChangedMtuOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    @Override
    public Observable<RxBleDevice> observeDisconnect() {
        return connectionStatePublishRelay
                .filter(new Predicate<Pair<RxBleDevice, RxBleConnection.RxBleConnectionState>>() {
                    @Override
                    public boolean test(
                            @NonNull Pair<RxBleDevice, RxBleConnection.RxBleConnectionState> pair
                    ) {
                        return pair.second == RxBleConnection.RxBleConnectionState.DISCONNECTED;
                    }
                })
                .map(new Function<Pair<RxBleDevice, RxBleConnection.RxBleConnectionState>, RxBleDevice>() {
                    @Override
                    public RxBleDevice apply(
                            @NonNull Pair<RxBleDevice, RxBleConnection.RxBleConnectionState> pair) {
                        return pair.first;
                    }
                })
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    @Override
    public Observable<RxBleDevice> observeConnect() {
        return connectionStatePublishRelay
                .filter(new Predicate<Pair<RxBleDevice, RxBleConnection.RxBleConnectionState>>() {
                    @Override
                    public boolean test(
                            @NonNull Pair<RxBleDevice, RxBleConnection.RxBleConnectionState> pair
                    ) {
                        return pair.second == RxBleConnection.RxBleConnectionState.CONNECTED;
                    }
                })
                .map(new Function<Pair<RxBleDevice, RxBleConnection.RxBleConnectionState>, RxBleDevice>() {
                    @Override
                    public RxBleDevice apply(
                            @NonNull Pair<RxBleDevice, RxBleConnection.RxBleConnectionState> pair
                    ) {
                        return pair.first;
                    }
                })
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    @Override
    public Observable<ServerResponseTransaction> getOnCharacteristicReadRequest(final UUID characteristic) {
        return withDisconnectionHandling(getReadCharacteristicOutput())
                .filter(new Predicate<GattServerTransaction<UUID>>() {
                    @Override
                    public boolean test(@NonNull GattServerTransaction<UUID> uuidGattServerTransaction) {
                        return uuidGattServerTransaction.getPayload().compareTo(characteristic) == 0;
                    }
                })
                .map(new Function<GattServerTransaction<UUID>, ServerResponseTransaction>() {
                    @Override
                    public ServerResponseTransaction apply(@NonNull GattServerTransaction<UUID> uuidGattServerTransaction) {
                        return uuidGattServerTransaction.getTransaction();
                    }
                })
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }


    @Override
    public Observable<ServerResponseTransaction> getOnCharacteristicWriteRequest(final UUID characteristic) {
        return withDisconnectionHandling(getWriteCharacteristicOutput())
                .filter(new Predicate<GattServerTransaction<UUID>>() {
                    @Override
                    public boolean test(@NonNull GattServerTransaction<UUID> uuidGattServerTransaction) {
                        return uuidGattServerTransaction.getPayload().compareTo(characteristic) == 0;
                    }
                })
                .map(new Function<GattServerTransaction<UUID>, ServerResponseTransaction>() {
                    @Override
                    public ServerResponseTransaction apply(@NonNull GattServerTransaction<UUID> uuidGattServerTransaction) {
                        return uuidGattServerTransaction.getTransaction();
                    }
                })
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    @Override
    public Observable<ServerResponseTransaction> getOnDescriptorReadRequest(
            final UUID characteristic,
            final UUID descriptor
    ) {
        return withDisconnectionHandling(getWriteDescriptorOutput())
                .filter(new Predicate<GattServerTransaction<BluetoothGattDescriptor>>() {
                    @Override
                    public boolean test(@NonNull GattServerTransaction<BluetoothGattDescriptor> transaction) {
                        return transaction.getPayload().getUuid().compareTo(descriptor) == 0
                                && transaction.getPayload().getCharacteristic().getUuid()
                                .compareTo(characteristic) == 0;
                    }
                })
                .map(new Function<GattServerTransaction<BluetoothGattDescriptor>, ServerResponseTransaction>() {
                    @Override
                    public ServerResponseTransaction apply(
                            @NonNull GattServerTransaction<BluetoothGattDescriptor> transaction
                    ) {
                        return transaction.getTransaction();
                    }
                })
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    @Override
    public Observable<ServerResponseTransaction> getOnDescriptorWriteRequest(
            final UUID characteristicuuid,
            final UUID descriptoruuid
    ) {
        return withDisconnectionHandling(getWriteDescriptorOutput())
                .filter(new Predicate<GattServerTransaction<BluetoothGattDescriptor>>() {
                    @Override
                    public boolean test(@NonNull GattServerTransaction<BluetoothGattDescriptor> transaction) {
                        return transaction.getPayload().getUuid().compareTo(descriptoruuid) == 0
                                && transaction.getPayload().getCharacteristic().getUuid()
                                .compareTo(characteristicuuid) == 0;
                    }
                })
                .map(new Function<GattServerTransaction<BluetoothGattDescriptor>, ServerResponseTransaction>() {
                    @Override
                    public ServerResponseTransaction apply(
                            @NonNull GattServerTransaction<BluetoothGattDescriptor> transaction
                    ) {
                        return transaction.getTransaction();
                    }
                })
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    @Override
    public Observable<Integer> getOnNotification() {
        return notificationPublishRelay.valueRelay;
    }

    @Override
    public Completable disconnect(final RxBleDevice device) {
        return operationQueue.queue(operationsProvider.provideDisconnectOperation(device)).ignoreElements();
    }

    @Override
    public void prepareDescriptorTransaction(
            final BluetoothGattDescriptor descriptor,
            int requestID,
            int offset, RxBleDevice device,
            PublishRelay<GattServerTransaction<BluetoothGattDescriptor>> valueRelay,
            byte[] value
    ) {
        final ServerResponseTransaction transaction = serverTransactionFactory.prepareCharacteristicTransaction(
                value,
                requestID,
                offset,
                device,
                descriptor.getUuid()
        );

        valueRelay.accept(new GattServerTransaction<>(descriptor, transaction));
    }

    @Override
    public void prepareCharacteristicTransaction(
            final BluetoothGattCharacteristic characteristic,
            int requestID,
            int offset, RxBleDevice device,
            PublishRelay<GattServerTransaction<UUID>> valueRelay,
            byte[] value
    ) {
        final ServerResponseTransaction transaction = serverTransactionFactory.prepareCharacteristicTransaction(
                value,
                requestID,
                offset,
                device,
                characteristic.getUuid()
        );
        valueRelay.accept(new GattServerTransaction<>(characteristic.getUuid(), transaction));
    }

    @Override
    public Observable<Boolean> blindAck(int requestID, int status, byte[] value, final RxBleDevice device) {
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
        getBluetoothGattServer().close();
        callbackScheduler.shutdown();
        compositeDisposable.dispose();
    }

    @Override
    public boolean isDisposed() {
        return compositeDisposable.isDisposed();
    }
}
