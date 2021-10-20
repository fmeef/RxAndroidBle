package com.polidea.rxandroidble2.internal.server;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.util.Pair;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.ClientComponent;
import com.polidea.rxandroidble2.ClientScope;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.exceptions.BleGattServerOperationType;
import com.polidea.rxandroidble2.internal.RxBleLog;

import java.util.concurrent.TimeUnit;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.Scheduler;

@ClientScope
public class RxBleGattServerCallback {
    //TODO: make this per device
    final PublishRelay<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>> connectionStatePublishRelay = PublishRelay.create();
    final Scheduler callbackScheduler;
    final BluetoothManager bluetoothManager;
    final RxBleServerState serverState;
    private final BluetoothGattServerProvider gattServerProvider;

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothDevice device, final int status, final int newState) {
            super.onConnectionStateChange(device, status, newState);
            RxBleLog.v("gatt server onConnectionStateChange: " + device.getAddress() + " " + status + " " + newState);
            RxBleServerConnectionInternal connectionInfo = gattServerProvider.getConnection(device);
            if (connectionInfo != null) {
                if (newState == BluetoothProfile.STATE_DISCONNECTED
                        || newState == BluetoothProfile.STATE_DISCONNECTING) {
                    connectionInfo.onDisconnectedException(
                            new BleDisconnectedException(device.getAddress(), status)
                    );
                    gattServerProvider.closeConnection(device);
                } else if (status != BluetoothGatt.GATT_SUCCESS) {
                        RxBleLog.e("GattServer state change failed %i", status);
                        //TODO: is this the same as client
                        connectionInfo.onGattConnectionStateException(
                                new BleGattServerException(
                                        status,
                                        device,
                                        BleGattServerOperationType.CONNECTION_STATE,
                                        "onConnectionStateChange GATT_FAILURE"
                                )
                        );
                        gattServerProvider.closeConnection(device);
                }
            } else {
                RxBleLog.e("connectionInfo was null for " + device);
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

            RxBleServerConnectionInternal connectionInfo = gattServerProvider.getConnection(device);

            if (connectionInfo == null) {
                RxBleLog.e("connectionInfo was null");
                return;
            }

            if (connectionInfo.getReadCharacteristicOutput().hasObservers()) {

                connectionInfo.prepareCharacteristicTransaction(
                        characteristic,
                        requestId,
                        offset,
                        device,
                        connectionInfo.getReadCharacteristicOutput().valueRelay,
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

            RxBleServerConnectionInternal connectionInfo = gattServerProvider.getConnection(device);

            if (connectionInfo == null) {
                RxBleLog.e("connectionInfo was null");
                return;
            }

            if (preparedWrite) {
                RxBleLog.d("characteristic long write");
                RxBleServerConnectionInternal.Output<byte[]> longWriteOuput
                        = connectionInfo.openLongWriteCharacteristicOutput(requestId, characteristic);
                longWriteOuput.valueRelay.accept(value);
            } else if (connectionInfo.getWriteCharacteristicOutput().hasObservers()) {
                connectionInfo.prepareCharacteristicTransaction(
                        characteristic,
                        requestId,
                        offset,
                        device,
                        connectionInfo.getWriteCharacteristicOutput().valueRelay,
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

            RxBleServerConnectionInternal connectionInfo = gattServerProvider.getConnection(device);

            if (connectionInfo == null) {
                RxBleLog.e("connectionInfo was null");
                return;
            }

            if (descriptor.getUuid().compareTo(RxBleClient.CLIENT_CONFIG) == 0) {
                connectionInfo.blindAck(
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        null
                )
                        .subscribe();
            }

            if (connectionInfo.getReadDescriptorOutput().hasObservers()) {
                connectionInfo.prepareDescriptorTransaction(
                        descriptor,
                        requestId,
                        offset,
                        device,
                        connectionInfo.getReadDescriptorOutput().valueRelay,
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

            RxBleServerConnectionInternal connectionInfo = gattServerProvider.getConnection(device);

            if (connectionInfo == null) {
                RxBleLog.e("connectionInfo was null");
                return;
            }

            if (preparedWrite) {
                RxBleLog.d("onDescriptorWriteRequest: invoking preparedWrite");
                RxBleServerConnectionInternal.Output<byte[]> longWriteOutput
                        = connectionInfo.openLongWriteDescriptorOutput(requestId, descriptor);
                longWriteOutput.valueRelay.accept(value); //TODO: offset?
            }  else {
                if (descriptor.getUuid().compareTo(RxBleClient.CLIENT_CONFIG) == 0) {
                    serverState.setNotifications(descriptor.getCharacteristic().getUuid(), value);
                    connectionInfo.blindAck(requestId, BluetoothGatt.GATT_SUCCESS, null)
                            .subscribe();
                }

                if (connectionInfo.getWriteDescriptorOutput().hasObservers()) {
                    connectionInfo.prepareDescriptorTransaction(
                            descriptor,
                            requestId,
                            offset,
                            device,
                            connectionInfo.getWriteDescriptorOutput().valueRelay,
                            value
                    );
                }
            }
        }

        @Override
        public void onExecuteWrite(final BluetoothDevice device, final int requestId, final boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            if (execute) {
                RxBleServerConnectionInternal connectionInfo = gattServerProvider.getConnection(device);

                if (connectionInfo == null) {
                    RxBleLog.e("connectionInfo was null");
                    return;
                }
                connectionInfo.closeLongWriteCharacteristicOutput(requestId);

                connectionInfo.resetCharacteristicMap();
                connectionInfo.resetDescriptorMap();
            }
        }

        @Override
        public void onNotificationSent(final BluetoothDevice device, final int status) {
            super.onNotificationSent(device, status);
            RxBleServerConnectionInternal connectionInfo = gattServerProvider.getConnection(device);

            if (connectionInfo == null) {
                RxBleLog.e("connectionInfo was null");
                return;
            }

            if (connectionInfo.getNotificationPublishRelay().hasObservers()) {
                RxBleLog.d("onNotificationSent: " + device.getAddress() + " " + status);
                connectionInfo.getNotificationPublishRelay().valueRelay.accept(
                        status
                );
            }
        }

        @Override
        public void onMtuChanged(final BluetoothDevice device, final int mtu) {
            super.onMtuChanged(device, mtu);

            RxBleServerConnectionInternal connectionInfo = gattServerProvider.getConnection(device);

            if (connectionInfo == null) {
                RxBleLog.e("connectionInfo was null");
                return;
            }

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

    @Inject
    public RxBleGattServerCallback(
            @Named(ClientComponent.NamedSchedulers.BLUETOOTH_CALLBACKS) Scheduler callbackScheduler,
            BluetoothGattServerProvider gattServerProvider,
            RxBleServerState serverState,
            BluetoothManager bluetoothManager
    ) {
        this.callbackScheduler = callbackScheduler;
        this.gattServerProvider = gattServerProvider;
        this.serverState = serverState;
        this.bluetoothManager = bluetoothManager;
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
    public Observable<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>> getOnConnectionStateChange() {
        return connectionStatePublishRelay.delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    public BluetoothGattServerCallback getBluetoothGattServerCallback() {
        return gattServerCallback;
    }
}
