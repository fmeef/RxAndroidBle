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

import androidx.annotation.NonNull;

import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDeviceServices;
import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleGattException;
import com.polidea.rxandroidble2.exceptions.BleGattServerCharacteristicException;
import com.polidea.rxandroidble2.exceptions.BleGattServerDescriptorException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.exceptions.BleGattServerOperationType;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.connection.BluetoothGattProvider;
import com.polidea.rxandroidble2.internal.connection.ConnectionScope;
import com.polidea.rxandroidble2.internal.connection.DisconnectionRouter;
import com.polidea.rxandroidble2.internal.util.ByteAssociation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.functions.Function;

@ConnectionScope
public class RxBleGattServerCallback {
    final Scheduler callbackScheduler;
    final BluetoothGattProvider<BluetoothDevice> bluetoothGattProvider;
    final DisconnectionRouter disconnectionRouter;
    //TODO: make this per device
    final PublishRelay<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>> connectionStatePublishRelay = PublishRelay.create();
    final RxBleGattServerCallback.Output<RxBleDeviceServices> servicesDiscoveredOutput = new RxBleGattServerCallback.Output<>();
    final Map<BluetoothDevice, ConnectionInfo> deviceConnectionInfoMap = new HashMap<>();

    private final Function<BleGattServerException, Observable<?>> errorMapper = new Function<BleGattServerException, Observable<?>>() {
        @Override
        public Observable<?> apply(BleGattServerException bleGattException) {
            return Observable.error(bleGattException);
        }
    };

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            bluetoothGattProvider.updateBluetoothGatt(device);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                RxBleLog.e("GattServer state change failed %i", status);
                disconnectionRouter.onGattConnectionStateException(
                        new BleGattServerException(status, device, BleGattServerOperationType.CONNECTION_STATE));
            }
            ConnectionInfo connectionInfo = getOrCreateConnectionInfo(device);
            connectionInfo.getConnectionStatePublishRelay()
                    .accept(mapConnectionStateToRxBleConnectionStatus(newState));

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectionRouter.onDisconnectedException(new BleDisconnectedException(device.getAddress(), status));
                deviceConnectionInfoMap.remove(device);
                deviceConnectionInfoMap.remove(device);
            }
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

            ConnectionInfo connectionInfo = getOrCreateConnectionInfo(device);


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

            ConnectionInfo connectionInfo = getOrCreateConnectionInfo(device);

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

            ConnectionInfo connectionInfo = getOrCreateConnectionInfo(device);

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

            ConnectionInfo connectionInfo = getOrCreateConnectionInfo(device);

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
                ConnectionInfo connectionInfo = getOrCreateConnectionInfo(device);

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

            ConnectionInfo connectionInfo = getOrCreateConnectionInfo(device);

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

            ConnectionInfo connectionInfo = getOrCreateConnectionInfo(device);

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

    private ConnectionInfo getOrCreateConnectionInfo(BluetoothDevice device) {
        ConnectionInfo connectionInfo = deviceConnectionInfoMap.get(device);

        if (connectionInfo == null) {
            connectionInfo = new ConnectionInfo();
            deviceConnectionInfoMap.put(device, connectionInfo);
        }
        return connectionInfo;
    }

    @Inject
    public RxBleGattServerCallback(
            @Named(ServerComponent.NamedSchedulers.BLUETOOTH_CALLBACKS) Scheduler callbackScheduler,
            BluetoothGattProvider<BluetoothDevice> bluetoothGattProvider,
            DisconnectionRouter disconnectionRouter
    ) {
        this.callbackScheduler = callbackScheduler;
        this.bluetoothGattProvider = bluetoothGattProvider;
        this.disconnectionRouter = disconnectionRouter;
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

    private ConnectionInfo getConnectionInfoOrError(BluetoothDevice device) throws Resources.NotFoundException {
        ConnectionInfo connectionInfo = deviceConnectionInfoMap.get(device);

        if (connectionInfo == null) {
            throw new Resources.NotFoundException("device does not exist");
        }
        return connectionInfo;
    }

    private <T> Observable<T> withDisconnectionHandling(RxBleGattServerCallback.Output<T> output) {
        //noinspection unchecked
        return Observable.merge(
                disconnectionRouter.<T>asErrorOnlyObservable(),
                output.valueRelay,
                (Observable<T>) output.errorRelay.flatMap(errorMapper)
        );
    }

    /**
     * @return Observable that never emits onNext.
     * @throws BleDisconnectedException emitted in case of a disconnect that is a part of the normal flow
     * @throws BleGattException         emitted in case of connection was interrupted unexpectedly.
     */
    public <T> Observable<T> observeDisconnect() {
        return disconnectionRouter.asErrorOnlyObservable();
    }

    /**
     * @return Observable that emits RxBleConnectionState that matches BluetoothGatt's state.
     * @param device device to observe
     * Does NOT emit errors even if status != GATT_SUCCESS.
     */
    public Observable<RxBleConnection.RxBleConnectionState> getOnConnectionStateChange(BluetoothDevice device) {
        ConnectionInfo connectionInfo =  getConnectionInfoOrError(device);

        return connectionInfo.getConnectionStatePublishRelay().delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    public Observable<Integer> getOnMtuChanged(BluetoothDevice device) {
        ConnectionInfo connectionInfo = getConnectionInfoOrError(device);

        return withDisconnectionHandling(connectionInfo.getChangedMtuOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    public BluetoothGattServerCallback getBluetoothGattServerCallback() {
        return gattServerCallback;
    }

    public Observable<ByteAssociation<UUID>> getOnCharacteristicReadRequest(BluetoothDevice device) {
        ConnectionInfo connectionInfo = getConnectionInfoOrError(device);

        return withDisconnectionHandling(connectionInfo.getReadCharacteristicOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    public Observable<ByteAssociation<UUID>> getOnCharacteristicWriteRequest(BluetoothDevice device) {
        ConnectionInfo connectionInfo = getConnectionInfoOrError(device);

        return withDisconnectionHandling(connectionInfo.getWriteCharacteristicOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    public Observable<ByteAssociation<BluetoothGattDescriptor>> getOnDescriptorReadRequest(BluetoothDevice device) {
        ConnectionInfo connectionInfo = getConnectionInfoOrError(device);

        return withDisconnectionHandling(connectionInfo.getReadDescriptorOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    public Observable<ByteAssociation<BluetoothGattDescriptor>> getOnDescriptorWriteRequest(BluetoothDevice device) {
        ConnectionInfo connectionInfo = getConnectionInfoOrError(device);

        return withDisconnectionHandling(connectionInfo.getWriteDescriptorOutput())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    public Observable<BluetoothDevice> getOnNotification(BluetoothDevice device) {
        ConnectionInfo connectionInfo = getConnectionInfoOrError(device);

        return withDisconnectionHandling(connectionInfo.getNotificationPublishRelay())
                .delay(0, TimeUnit.SECONDS, callbackScheduler);
    }

    private static class ConnectionInfo {
        private final RxBleGattServerCallback.Output<ByteAssociation<UUID>> readCharacteristicOutput =
                new RxBleGattServerCallback.Output<>();
        private final RxBleGattServerCallback.Output<ByteAssociation<UUID>> writeCharacteristicOutput =
                new RxBleGattServerCallback.Output<>();
        private final RxBleGattServerCallback.Output<ByteAssociation<BluetoothGattDescriptor>> readDescriptorOutput =
                new RxBleGattServerCallback.Output<>();
        private final RxBleGattServerCallback.Output<ByteAssociation<BluetoothGattDescriptor>> writeDescriptorOutput =
                new RxBleGattServerCallback.Output<>();
        private final PublishRelay<RxBleConnection.RxBleConnectionState> connectionStatePublishRelay =
                PublishRelay.create();
        private final RxBleGattServerCallback.Output<BluetoothDevice> notificationPublishRelay =
                new RxBleGattServerCallback.Output<>();
        private final RxBleGattServerCallback.Output<Integer> changedMtuOutput =
                new RxBleGattServerCallback.Output<>();
        private final Map<BluetoothGattCharacteristic, ByteArrayOutputStream> characteristicByteArrayOutputStreamMap =
                new HashMap<>();
        private final Map<BluetoothGattDescriptor, ByteArrayOutputStream> descriptorByteArrayOutputStreamMap =
                new HashMap<>();

        ConnectionInfo() {
        }

        @NonNull
        public Output<ByteAssociation<UUID>> getReadCharacteristicOutput() {
            return readCharacteristicOutput;
        }

        @NonNull
        public Output<ByteAssociation<UUID>> getWriteCharacteristicOutput() {
            return writeCharacteristicOutput;
        }

        @NonNull
        public Output<ByteAssociation<BluetoothGattDescriptor>> getReadDescriptorOutput() {
            return readDescriptorOutput;
        }

        @NonNull
        public Output<ByteAssociation<BluetoothGattDescriptor>> getWriteDescriptorOutput() {
            return writeDescriptorOutput;
        }

        @NonNull
        public PublishRelay<RxBleConnection.RxBleConnectionState> getConnectionStatePublishRelay() {
            return connectionStatePublishRelay;
        }

        @NonNull
        public RxBleGattServerCallback.Output<BluetoothDevice> getNotificationPublishRelay() {
            return notificationPublishRelay;
        }

        @NonNull
        public Output<Integer> getChangedMtuOutput() {
            return changedMtuOutput;
        }

        public boolean writeCharacteristicBytes(BluetoothGattCharacteristic characteristic, byte[] bytes) {
            try {
                ByteArrayOutputStream os = characteristicByteArrayOutputStreamMap.get(characteristic);
                if (os == null) {
                    return false;
                }
                os.write(bytes);
            } catch (IOException e) {
                return false;
            }
            return true;
        }

        public boolean writeDescriptorBytes(BluetoothGattDescriptor descriptor, byte[] bytes) {
            try {
                ByteArrayOutputStream os = descriptorByteArrayOutputStreamMap.get(descriptor);
                if (os == null) {
                    return false;
                }
                os.write(bytes);
            } catch (IOException e) {
                return false;
            }
            return true;
        }

        @NonNull
        public ByteArrayOutputStream getDescriptorLongWriteStream(BluetoothGattDescriptor descriptor) {
            ByteArrayOutputStream os = descriptorByteArrayOutputStreamMap.get(descriptor);
            if (os == null) {
                os = new ByteArrayOutputStream();
                descriptorByteArrayOutputStreamMap.put(descriptor, os);
            }

            return os;
        }

        @NonNull
        public Map<BluetoothGattCharacteristic, ByteArrayOutputStream> getCharacteristicLongWriteStreamMap() {
            return characteristicByteArrayOutputStreamMap;
        }

        @NonNull
        public Map<BluetoothGattDescriptor, ByteArrayOutputStream> getDescriptorByteArrayOutputStreamMap() {
            return descriptorByteArrayOutputStreamMap;
        }

        public void resetDescriptorMap() {
            descriptorByteArrayOutputStreamMap.clear();
        }

        public void resetCharacteristicMap() {
            characteristicByteArrayOutputStreamMap.clear();
        }
    }

    private static class Output<T> {

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
