package com.polidea.rxandroidble2.internal.server;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.util.Pair;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.ServerScope;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.exceptions.BleGattServerOperationType;
import com.polidea.rxandroidble2.internal.RxBleLog;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

import static com.polidea.rxandroidble2.RxBleServerImpl.TAG;

@ServerScope
public class RxBleGattServerCallback {
    //TODO: make this per device
    final PublishRelay<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>> connectionStatePublishRelay = PublishRelay.create();
    final Scheduler callbackScheduler;
    final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final BluetoothGattServerProvider gattServerProvider;

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothDevice device, final int status, final int newState) {
            super.onConnectionStateChange(device, status, newState);

            if (device == null) {
                return;
            }
            final Disposable d = getOrCreateConnectionInfo(device)
                    .subscribeOn(callbackScheduler)
                    .subscribe(new Consumer<RxBleServerConnection>() {
                        @Override
                        public void accept(RxBleServerConnection connectionInfo) throws Exception {
                            if (newState == BluetoothProfile.STATE_DISCONNECTED
                                    || newState == BluetoothProfile.STATE_DISCONNECTING) {
                                connectionInfo.getDisconnectionRouter().onDisconnectedException(
                                        new BleDisconnectedException(device.getAddress(), status)
                                );
                            } else {
                                if (status != BluetoothGatt.GATT_SUCCESS) {
                                    RxBleLog.e("GattServer state change failed %i", status);
                                    //TODO: is this the same as client
                                    connectionInfo.getDisconnectionRouter().onGattConnectionStateException(
                                            new BleGattServerException(status, device, BleGattServerOperationType.CONNECTION_STATE)
                                    );
                                }
                            }
                        }
                    });
            if (gattServerProvider.getConnection(device) != null) {
                compositeDisposable.add(d);
            } else {
                d.dispose();
            }

            connectionStatePublishRelay.accept(new Pair<>(
                    device,
                    mapConnectionStateToRxBleConnectionStatus(newState)
            ));
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

            Disposable d = getOrCreateConnectionInfo(device)
                    .subscribeOn(callbackScheduler)
                    .subscribe(new Consumer<RxBleServerConnection>() {
                        @Override
                        public void accept(RxBleServerConnection connectionInfo) throws Exception {
                            if (connectionInfo.getReadCharacteristicOutput().hasObservers()) {

                                connectionInfo.prepareCharacteristicTransaction(
                                        characteristic,
                                        requestId,
                                        offset,
                                        device,
                                        connectionInfo.getReadCharacteristicOutput().valueRelay
                                );
                            }
                        }
                    });

            compositeDisposable.add(d);
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

            Disposable d = getOrCreateConnectionInfo(device)
                    .subscribeOn(callbackScheduler)
                    .subscribe(new Consumer<RxBleServerConnection>() {
                        @Override
                        public void accept(RxBleServerConnection connectionInfo) throws Exception {

                            if (preparedWrite) {
                                RxBleServerConnection.Output<byte[]> longWriteOuput
                                        = connectionInfo.openLongWriteCharacteristicOutput(requestId, characteristic);
                                longWriteOuput.valueRelay.accept(value);
                            } else if (connectionInfo.getWriteCharacteristicOutput().hasObservers()) {
                                connectionInfo.prepareCharacteristicTransaction(
                                        characteristic,
                                        requestId,
                                        offset,
                                        device,
                                        connectionInfo.getWriteCharacteristicOutput().valueRelay
                                );
                            }
                        }
                    });

            compositeDisposable.add(d);
        }

        @Override
        public void onDescriptorReadRequest(final BluetoothDevice device,
                                            final int requestId,
                                            final int offset,
                                            final BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);

            Disposable d = getOrCreateConnectionInfo(device)
                    .subscribeOn(callbackScheduler)
                    .subscribe(new Consumer<RxBleServerConnection>() {
                        @Override
                        public void accept(RxBleServerConnection connectionInfo) throws Exception {

                            if (connectionInfo.getReadDescriptorOutput().hasObservers()) {
                                connectionInfo.prepareDescriptorTransaction(
                                        descriptor,
                                        requestId,
                                        offset,
                                        device,
                                        connectionInfo.getReadDescriptorOutput().valueRelay
                                );
                            }
                        }
                    });

            compositeDisposable.add(d);
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

            Disposable d = getOrCreateConnectionInfo(device)
                    .subscribeOn(callbackScheduler)
                    .subscribe(new Consumer<RxBleServerConnection>() {
                        @Override
                        public void accept(RxBleServerConnection connectionInfo) throws Exception {
                            if (preparedWrite) {
                                RxBleServerConnection.Output<byte[]> longWriteOutput
                                        = connectionInfo.openLongWriteDescriptorOutput(requestId, descriptor);
                                longWriteOutput.valueRelay.accept(value); //TODO: offset?
                            } else if (connectionInfo.getWriteDescriptorOutput().hasObservers()) {
                                connectionInfo.prepareDescriptorTransaction(
                                        descriptor,
                                        requestId,
                                        offset,
                                        device,
                                        connectionInfo.getWriteDescriptorOutput().valueRelay
                                );
                            }
                        }
                    });
            compositeDisposable.add(d);
        }

        @Override
        public void onExecuteWrite(final BluetoothDevice device, final int requestId, final boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            if (execute) {
                Disposable d = getOrCreateConnectionInfo(device)
                        .subscribeOn(callbackScheduler)
                        .subscribe(new Consumer<RxBleServerConnection>() {
                            @Override
                            public void accept(RxBleServerConnection connectionInfo) throws Exception {
                                connectionInfo.closeLongWriteCharacteristicOutput(requestId);

                                connectionInfo.resetCharacteristicMap();
                                connectionInfo.resetDescriptorMap();
                            }
                        });
                compositeDisposable.add(d);
            }
        }

        @Override
        public void onNotificationSent(final BluetoothDevice device, final int status) {
            super.onNotificationSent(device, status);

            Disposable d = getOrCreateConnectionInfo(device)
                    .subscribeOn(callbackScheduler)
                    .subscribe(new Consumer<RxBleServerConnection>() {
                        @Override
                        public void accept(RxBleServerConnection connectionInfo) throws Exception {
                            if (connectionInfo.getNotificationPublishRelay().hasObservers()) {
                                connectionInfo.getNotificationPublishRelay().valueRelay.accept(
                                        device
                                );
                            }
                        }
                    });
            compositeDisposable.add(d);
        }

        @Override
        public void onMtuChanged(final BluetoothDevice device, final int mtu) {
            super.onMtuChanged(device, mtu);

            Disposable d = getOrCreateConnectionInfo(device)
                    .subscribeOn(callbackScheduler)
                    .subscribe(new Consumer<RxBleServerConnection>() {
                        @Override
                        public void accept(RxBleServerConnection connectionInfo) throws Exception {
                            if (connectionInfo.getChangedMtuOutput().hasObservers()) {
                                connectionInfo.getChangedMtuOutput().valueRelay.accept(mtu);
                            }
                        }
                    });
            compositeDisposable.add(d);
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

    private synchronized Single<RxBleServerConnection> getOrCreateConnectionInfo(final BluetoothDevice device) {
        RxBleServerConnection connection = gattServerProvider.getConnection(device);
        if (connection == null) {
            RxBleLog.e(TAG, "attempted to get nonexistent connection");
            return Single.never();
        } else {
            RxBleLog.e(TAG, "returned connection");
            return Single.just(connection);
        }
    }

    @Inject
    public RxBleGattServerCallback(
            @Named(ServerComponent.NamedSchedulers.BLUETOOTH_CALLBACK) Scheduler callbackScheduler,
            BluetoothGattServerProvider gattServerProvider
    ) {
        this.callbackScheduler = callbackScheduler;
        this.gattServerProvider = gattServerProvider;
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

    public Single<RxBleServerConnection> getRxBleServerConnection(BluetoothDevice device) {
        return getOrCreateConnectionInfo(device);
    }

}