package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.os.DeadObjectException;

import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.exceptions.BleGattServerOperationType;
import com.polidea.rxandroidble2.internal.QueueOperation;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.serialization.QueueReleaseInterface;
import com.polidea.rxandroidble2.internal.server.BluetoothGattServerProvider;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternal;
import com.polidea.rxandroidble2.internal.util.QueueReleasingEmitterWrapper;

import io.reactivex.ObservableEmitter;
import io.reactivex.Single;

public class NotifyCharacteristicChangedOperation extends QueueOperation<Integer> {

    private final BluetoothGattServerProvider serverProvider;
    private final BluetoothGattCharacteristic characteristic;
    private final TimeoutConfiguration timeoutConfiguration;
    private final RxBleServerConnectionInternal connection;
    private final byte[] value;
    private final boolean isIndication;


    public NotifyCharacteristicChangedOperation(
            BluetoothGattServerProvider serverProvider,
            BluetoothGattCharacteristic characteristic,
            TimeoutConfiguration timeoutConfiguration,
            RxBleServerConnectionInternal connection,
            byte[] value,
            boolean isindication
            ) {
        this.serverProvider = serverProvider;
        this.characteristic = characteristic;
        this.timeoutConfiguration = timeoutConfiguration;
        this.connection = connection;
        this.value = value;
        this.isIndication = isindication;
    }


    @Override
    protected void protectedRun(
            final ObservableEmitter<Integer> emitter,
            final QueueReleaseInterface queueReleaseInterface
    ) throws Throwable {
        final BluetoothGattServer server = serverProvider.getBluetoothGatt();
        final QueueReleasingEmitterWrapper<Integer> emitterWrapper = new QueueReleasingEmitterWrapper<>(emitter, queueReleaseInterface);
        if (server == null) {
            RxBleLog.w("NotificationSendOperation encountered null gatt server");
            emitter.onError(new BleGattServerException(
                    connection.getDevice(),
                    BleGattServerOperationType.CONNECTION_STATE,
                    "server handle was null in NotifyCharacteristicChangedOperation"
                    )
            );
            emitterWrapper.cancel();
        } else if (characteristic.getService() == null) {
            emitterWrapper.onError(new BleGattServerException(
                    connection.getDevice(),
                    BleGattServerOperationType.NOTIFICATION_SENT,
                    "service for characteristic " + characteristic.getUuid() + " was null"
            ));
        } else {
            RxBleLog.d("running notifycharacteristic");


            getCompleted()
                    .timeout(
                            timeoutConfiguration.timeout,
                            timeoutConfiguration.timeoutTimeUnit,
                            timeoutConfiguration.timeoutScheduler
                    )
                    .toObservable()
                    .subscribe(emitterWrapper);
            characteristic.setValue(value);
            if (!server.notifyCharacteristicChanged(connection.getDevice(), characteristic, isIndication)) {
                emitterWrapper.onError(new BleGattServerException(
                        connection.getDevice(),
                        BleGattServerOperationType.CONNECTION_STATE,
                        "NotifyCharacteristicChangedOperation failed"
                ));
            }
        }
    }

    private Single<Integer> getCompleted() {
        return connection.getOnNotification().firstOrError();
    }

    @Override
    protected BleException provideException(DeadObjectException deadObjectException) {
        return new BleGattServerException(
                connection.getDevice(),
                BleGattServerOperationType.NOTIFICATION_SENT,
                "notification failed"
        );
    }
}
