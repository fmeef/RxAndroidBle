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
import com.polidea.rxandroidble2.ServerScope;
import com.polidea.rxandroidble2.exceptions.BleGattServerCharacteristicException;
import com.polidea.rxandroidble2.exceptions.BleGattServerDescriptorException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.exceptions.BleGattServerOperationType;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueue;
import com.polidea.rxandroidble2.internal.util.ByteAssociation;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.Scheduler;

@ServerScope
public class RxBleGattServerCallback {
    final Scheduler callbackScheduler;
    final ServerOperationQueue serverOperationQueue;
    //TODO: make this per device
    final PublishRelay<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>> connectionStatePublishRelay = PublishRelay.create();
    final Map<BluetoothDevice, RxBleServerConnection> deviceConnectionInfoMap = new HashMap<>();
    final ServerConnectionComponent.Builder connectionComponentBuilder;



    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                RxBleLog.e("GattServer state change failed %i", status);
                //TODO: route connect failures
            }
            RxBleServerConnection connectionInfo = getOrCreateConnectionInfo(device);

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                deviceConnectionInfoMap.remove(device);
                deviceConnectionInfoMap.remove(device);
            } else {
                deviceConnectionInfoMap.put(device, connectionInfo);
            }

            connectionStatePublishRelay.accept(new Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>(
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
        public void onCharacteristicReadRequest(BluetoothDevice device,
                                                int requestId,
                                                int offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

            RxBleServerConnection connectionInfo = getOrCreateConnectionInfo(device);


            if (connectionInfo.getReadCharacteristicOutput().hasObservers() && !propagateErrorIfOccurred(
                    connectionInfo.getReadCharacteristicOutput(),
                    device,
                    -1,
                    BleGattServerOperationType.CHARACTERISTIC_READ_REQUEST
            )) {
                connectionInfo.getReadCharacteristicOutput().valueRelay.accept(
                        new ByteAssociation<>(characteristic.getUuid(), characteristic.getValue())
                );
            }

        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                 int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset,
                                                 byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

            RxBleServerConnection connectionInfo = getOrCreateConnectionInfo(device);

            if (preparedWrite) {
                if (!connectionInfo.writeCharacteristicBytes(characteristic, value)) {
                    throw new BleGattServerException(-1, device, BleGattServerOperationType.CHARACTERISTIC_LONG_WRITE_REQUEST);
                }
            } else if (connectionInfo.getWriteCharacteristicOutput().hasObservers() && !propagateErrorIfOccurred(
                        connectionInfo.getWriteCharacteristicOutput(),
                        device,
                        -1,
                        BleGattServerOperationType.CHARACTERISTIC_WRITE_REQUEST
            )) {
                connectionInfo.getWriteCharacteristicOutput().valueRelay.accept(
                        new ByteAssociation<>(characteristic.getUuid(), characteristic.getValue())
                );
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device,
                                            int requestId,
                                            int offset,
                                            BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);

            RxBleServerConnection connectionInfo = getOrCreateConnectionInfo(device);

            if (connectionInfo.getReadDescriptorOutput().hasObservers() && !propagateErrorIfOccurred(
                    connectionInfo.getReadDescriptorOutput(),
                    device,
                    -1,
                    BleGattServerOperationType.DESCRIPTOR_READ_REQUEST
            )) {
                connectionInfo.getReadDescriptorOutput().valueRelay.accept(
                        new ByteAssociation<>(descriptor, descriptor.getValue())
                );
            }

        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device,
                                             int requestId,
                                             BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite,
                                             boolean responseNeeded,
                                             int offset,
                                             byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);

            RxBleServerConnection connectionInfo = getOrCreateConnectionInfo(device);

            if (preparedWrite) {
                if (!connectionInfo.writeDescriptorBytes(descriptor, value)) {
                    throw new BleGattServerException(-1, device,
                            BleGattServerOperationType.CHARACTERISTIC_LONG_WRITE_REQUEST);
                }
            } else if (connectionInfo.getWriteDescriptorOutput().hasObservers() && !propagateErrorIfOccurred(
                    connectionInfo.getWriteDescriptorOutput(),
                    device,
                    -1,
                    BleGattServerOperationType.DESCRIPTOR_WRITE_REQUEST
            )) {
                connectionInfo.getWriteDescriptorOutput().valueRelay.accept(
                        new ByteAssociation<>(descriptor, descriptor.getValue())
                );
            }
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            if (execute) {
                RxBleServerConnection connectionInfo = getOrCreateConnectionInfo(device);

                for (Map.Entry<BluetoothGattCharacteristic, ByteArrayOutputStream>  entry
                        : connectionInfo.getCharacteristicLongWriteStreamMap().entrySet()) {
                    ByteArrayOutputStream outputStream = entry.getValue();
                    BluetoothGattCharacteristic characteristic = entry.getKey();
                    if (connectionInfo.getWriteCharacteristicOutput().hasObservers() && !propagateErrorIfOccurred(
                            connectionInfo.getWriteCharacteristicOutput(),
                            device,
                            -1,
                            BleGattServerOperationType.CHARACTERISTIC_LONG_WRITE_REQUEST
                        )) {
                        connectionInfo.getWriteCharacteristicOutput().valueRelay.accept(
                                new ByteAssociation<>(characteristic.getUuid(), outputStream.toByteArray())
                        );
                    }
                }

                for (Map.Entry<BluetoothGattDescriptor, ByteArrayOutputStream> entry
                        : connectionInfo.getDescriptorByteArrayOutputStreamMap().entrySet()) {
                    ByteArrayOutputStream outputStream = entry.getValue();
                    BluetoothGattDescriptor descriptor = entry.getKey();

                    if (connectionInfo.getWriteDescriptorOutput().hasObservers() && !propagateErrorIfOccurred(
                            connectionInfo.getWriteDescriptorOutput(),
                            device,
                            -1,
                            BleGattServerOperationType.DESCRIPTOR_LONG_WRITE_REQUEST
                    )) {
                        connectionInfo.getWriteDescriptorOutput().valueRelay.accept(
                                new ByteAssociation<>(descriptor, outputStream.toByteArray())
                        );
                    }
                }

                connectionInfo.resetCharacteristicMap();
                connectionInfo.resetDescriptorMap();
            }

            //TODO: implement long writes
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);

            RxBleServerConnection connectionInfo = getOrCreateConnectionInfo(device);

            if (connectionInfo.getNotificationPublishRelay().hasObservers() && !propagateErrorIfOccurred(
                    connectionInfo.getNotificationPublishRelay(),
                    device,
                    status,
                    BleGattServerOperationType.NOTIFICATION_SENT
            )) {
                connectionInfo.getNotificationPublishRelay().valueRelay.accept(
                        device
                );
            }

        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);

            RxBleServerConnection connectionInfo = getOrCreateConnectionInfo(device);

            if (connectionInfo.getChangedMtuOutput().hasObservers()
                    && !propagateErrorIfOccurred(
                            connectionInfo.getChangedMtuOutput(),
                            device,
                            -1,
                            BleGattServerOperationType.ON_MTU_CHANGED
            )) {
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
            connectionInfo =  connectionComponentBuilder.build().serverConnection();
            deviceConnectionInfoMap.put(device, connectionInfo);
        }
        return connectionInfo;
    }

    @Inject
    public RxBleGattServerCallback(
            @Named(ServerComponent.NamedSchedulers.BLUETOOTH_CALLBACKS) Scheduler callbackScheduler,
            ServerConnectionComponent.Builder connectionComponentBuilder,
            ServerOperationQueue serverOperationQueue
    ) {
        this.callbackScheduler = callbackScheduler;
        this.connectionComponentBuilder = connectionComponentBuilder;
        this.serverOperationQueue = serverOperationQueue;
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

    static boolean propagateErrorIfOccurred(
            RxBleGattServerCallback.Output<?> output,
            BluetoothDevice device,
            BluetoothGattCharacteristic characteristic,
            int status,
            BleGattServerOperationType operationType
    ) {
        return isException(status) && propagateStatusError(output, new BleGattServerCharacteristicException(
                characteristic,
                device,
                status,
                operationType
        ));
    }

    static boolean propagateErrorIfOccurred(
            RxBleGattServerCallback.Output<?> output,
            BluetoothDevice device,
            BluetoothGattDescriptor descriptor,
            int status,
            BleGattServerOperationType operationType
    ) {
        return isException(status) && propagateStatusError(output, new BleGattServerDescriptorException(
                descriptor,
                device,
                status,
                operationType
        ));
    }

    static boolean propagateErrorIfOccurred(RxBleGattServerCallback.Output<?> output,
                                            BluetoothDevice device,
                                            int status,
                                            BleGattServerOperationType operationType) {
        return isException(status) && propagateStatusError(output,
                new BleGattServerException(status, device, operationType));
    }

    private static boolean isException(int status) {
        return status != BluetoothGatt.GATT_SUCCESS;
    }

    private static boolean propagateStatusError(RxBleGattServerCallback.Output<?> output, BleGattServerException exception) {
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
        return deviceConnectionInfoMap.get(device);
    }

    public static class Output<T> {

        final PublishRelay<T> valueRelay;
        final PublishRelay<BleGattServerException> errorRelay;

        Output() {
            this.valueRelay = PublishRelay.create();
            this.errorRelay = PublishRelay.create();
        }

        boolean hasObservers() {
            return valueRelay.hasObservers() || errorRelay.hasObservers();
        }
    }

}
