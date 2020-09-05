package com.polidea.rxandroidble2.internal.server;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.res.Resources;
import android.util.Pair;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.ServerConnectionComponent;
import com.polidea.rxandroidble2.ServerResponseTransaction;
import com.polidea.rxandroidble2.ServerScope;
import com.polidea.rxandroidble2.ServerTransactionFactory;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.exceptions.BleGattServerOperationType;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.util.GattServerTransaction;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;

@ServerScope
public class RxBleGattServerCallback {
    //TODO: make this per device
    final PublishRelay<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>> connectionStatePublishRelay = PublishRelay.create();
    final Map<BluetoothDevice, RxBleServerConnection> deviceConnectionInfoMap = new HashMap<>();
    final ServerConnectionComponent.Builder connectionComponentBuilder;
    final ServerTransactionFactory serverTransactionFactory;
    final Scheduler callbackScheduler;
    final CompositeDisposable compositeDisposable = new CompositeDisposable();

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);

            if (device == null) {
                return;
            }
            RxBleServerConnection connectionInfo = getOrCreateConnectionInfo(device);

            if (newState == BluetoothProfile.STATE_DISCONNECTED
                    || newState == BluetoothProfile.STATE_DISCONNECTING) {
                connectionInfo.getDisconnectionRouter().onDisconnectedException(
                        new BleDisconnectedException(device.getAddress(), status)
                );
                deviceConnectionInfoMap.remove(device);
            } else {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    RxBleLog.e("GattServer state change failed %i", status);
                    //TODO: is this the same as client
                    connectionInfo.getDisconnectionRouter().onGattConnectionStateException(
                            new BleGattServerException(status, device, BleGattServerOperationType.CONNECTION_STATE)
                    );
                }
                deviceConnectionInfoMap.put(device, connectionInfo);
            }

            connectionStatePublishRelay.accept(new Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>(
                    connectionInfo.getDevice(),
                    mapConnectionStateToRxBleConnectionStatus(newState)
            ));
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            //TODO:
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device,
                                                int requestId,
                                                int offset,
                                                final BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

            RxBleServerConnection connectionInfo = getOrCreateConnectionInfo(device);


            if (connectionInfo.getReadCharacteristicOutput().hasObservers()) {

                Disposable disposable = serverTransactionFactory.prepareCharacteristicTransaction(
                        characteristic.getValue(),
                        requestId,
                        offset,
                        device
                ).map(new Function<ServerResponseTransaction, GattServerTransaction<UUID>>() {
                            @Override
                            public GattServerTransaction<UUID> apply(
                                    ServerResponseTransaction serverResponseTransaction
                            ) throws Exception {
                                return new GattServerTransaction<>(characteristic.getUuid(), serverResponseTransaction);
                            }
                        })
                        .subscribeOn(callbackScheduler)
                        .subscribe(connectionInfo.getReadCharacteristicOutput().valueRelay);

                compositeDisposable.add(disposable);
            }

        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                 int requestId,
                                                 final BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset,
                                                 byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

            RxBleServerConnection connectionInfo = getOrCreateConnectionInfo(device);

            if (preparedWrite) {
                RxBleServerConnection.Output<byte[]> longWriteOuput
                        = connectionInfo.openLongWriteCharacteristicOutput(requestId, characteristic);
                if (longWriteOuput == null) {
                    throw new BleGattServerException(-1, device, BleGattServerOperationType.CHARACTERISTIC_LONG_WRITE_REQUEST);
                }
                longWriteOuput.valueRelay.accept(value);
            } else if (connectionInfo.getWriteCharacteristicOutput().hasObservers()) {
                Disposable disposable = serverTransactionFactory.prepareCharacteristicTransaction(
                        value,
                        requestId,
                        offset,
                        device
                ).map(new Function<ServerResponseTransaction, GattServerTransaction<UUID>>() {
                    @Override
                    public GattServerTransaction<UUID> apply(ServerResponseTransaction serverResponseTransaction) throws Exception {
                        return new GattServerTransaction<>(characteristic.getUuid(), serverResponseTransaction);
                    }
                })
                        .subscribeOn(callbackScheduler)
                        .subscribe(connectionInfo.getWriteCharacteristicOutput().valueRelay);
                compositeDisposable.add(disposable);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device,
                                            int requestId,
                                            int offset,
                                            final BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);

            RxBleServerConnection connectionInfo = getOrCreateConnectionInfo(device);

            if (connectionInfo.getReadDescriptorOutput().hasObservers()) {
                Disposable disposable = serverTransactionFactory.prepareCharacteristicTransaction(
                        descriptor.getValue(),
                        requestId,
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
                        .subscribe(connectionInfo.getReadDescriptorOutput().valueRelay);
                compositeDisposable.add(disposable);
            }

        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device,
                                             int requestId,
                                             final BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite,
                                             boolean responseNeeded,
                                             int offset,
                                             byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);

            RxBleServerConnection connectionInfo = getOrCreateConnectionInfo(device);

            if (preparedWrite) {
                RxBleServerConnection.Output<byte[]> longWriteOutput = connectionInfo.openLongWriteDescriptorOutput(requestId, descriptor);
                if (longWriteOutput == null) {
                    throw new BleGattServerException(-1, device, BleGattServerOperationType.DESCRIPTOR_LONG_WRITE_REQUEST);
                }
                longWriteOutput.valueRelay.accept(value); //TODO: offset?
            } else if (connectionInfo.getWriteDescriptorOutput().hasObservers()) {
                Disposable disposable = serverTransactionFactory.prepareCharacteristicTransaction(
                        value,
                        requestId,
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
                        .subscribe(connectionInfo.getWriteDescriptorOutput().valueRelay);
                compositeDisposable.add(disposable);
            }
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            if (execute) {
                RxBleServerConnection connectionInfo = getOrCreateConnectionInfo(device);

                connectionInfo.closeLongWriteCharacteristicOutput(requestId);

                connectionInfo.resetCharacteristicMap();
                connectionInfo.resetDescriptorMap();
            }
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);

            RxBleServerConnection connectionInfo = getOrCreateConnectionInfo(device);

            if (connectionInfo.getNotificationPublishRelay().hasObservers()) {
                connectionInfo.getNotificationPublishRelay().valueRelay.accept(
                        device
                );
            }

        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);

            RxBleServerConnection connectionInfo = getOrCreateConnectionInfo(device);

            if (connectionInfo.getChangedMtuOutput().hasObservers()) {
                connectionInfo.getChangedMtuOutput().valueRelay.accept(mtu);
            }
        }

        @Override
        public void onPhyUpdate(BluetoothDevice device, int txPhy, int rxPhy, int status) {
            super.onPhyUpdate(device, txPhy, rxPhy, status);
        }

        @Override
        public void onPhyRead(BluetoothDevice device, int txPhy, int rxPhy, int status) {
            super.onPhyRead(device, txPhy, rxPhy, status);
        }
    };

    private RxBleServerConnection getOrCreateConnectionInfo(BluetoothDevice device) {
        RxBleServerConnection connectionInfo = deviceConnectionInfoMap.get(device);

        if (connectionInfo == null) {
            connectionInfo =  connectionComponentBuilder
                    .bluetoothDevice(device)
                    .build()
                    .serverConnection();
            deviceConnectionInfoMap.put(device, connectionInfo);
        }
        return connectionInfo;
    }

    @Inject
    public RxBleGattServerCallback(
            ServerConnectionComponent.Builder connectionComponentBuilder,
            ServerTransactionFactory serverTransactionFactory,
            @Named(ServerComponent.NamedSchedulers.BLUETOOTH_CALLBACK) Scheduler callbackScheduler
    ) {
        this.connectionComponentBuilder = connectionComponentBuilder;
        this.serverTransactionFactory = serverTransactionFactory;
        this.callbackScheduler = callbackScheduler;
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

    private static boolean propagateStatusError(RxBleServerConnection.Output<?> output, BleGattServerException exception) {
        output.errorRelay.accept(exception);
        return true;
    }

    private RxBleServerConnection getConnectionInfoOrError(BluetoothDevice device) throws Resources.NotFoundException {
        RxBleServerConnection connectionInfo = deviceConnectionInfoMap.get(device);

        if (connectionInfo == null) {
            throw new Resources.NotFoundException("device does not exist");
        }
        return connectionInfo;
    }

    /**
     * @return Observable that emits RxBleConnectionState that matches BluetoothGatt's state.
     * Does NOT emit errors even if status != GATT_SUCCESS.
     */
    public Observable<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>> getOnConnectionStateChange() {
        return connectionStatePublishRelay;
    }

    public BluetoothGattServerCallback getBluetoothGattServerCallback() {
        return gattServerCallback;
    }

    public RxBleServerConnection getRxBleServerConnection(BluetoothDevice device) {
        return getOrCreateConnectionInfo(device);
    }

}
