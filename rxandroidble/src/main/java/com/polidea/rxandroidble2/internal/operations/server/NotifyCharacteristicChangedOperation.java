package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.os.DeadObjectException;

import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.exceptions.BleGattServerOperationType;
import com.polidea.rxandroidble2.internal.QueueOperation;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.serialization.QueueReleaseInterface;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternal;
import com.polidea.rxandroidble2.internal.util.QueueReleasingEmitterWrapper;

import io.reactivex.ObservableEmitter;
import io.reactivex.Single;
import io.reactivex.functions.Action;

public class NotifyCharacteristicChangedOperation extends QueueOperation<Integer> {

    private final BluetoothGattServer server;
    private final BluetoothGattCharacteristic characteristic;
    private final TimeoutConfiguration timeoutConfiguration;
    private final RxBleServerConnectionInternal connection;
    private final byte[] value;
    private final boolean isIndication;
    private final RxBleDevice device;


    public NotifyCharacteristicChangedOperation(
            BluetoothGattServer server,
            BluetoothGattCharacteristic characteristic,
            TimeoutConfiguration timeoutConfiguration,
            RxBleServerConnectionInternal connection,
            byte[] value,
            boolean isindication,
            RxBleDevice device
            ) {
        this.server = server;
        this.characteristic = characteristic;
        this.timeoutConfiguration = timeoutConfiguration;
        this.connection = connection;
        this.value = value;
        this.isIndication = isindication;
        this.device = device;
    }


    @Override
    protected void protectedRun(
            final ObservableEmitter<Integer> emitter,
            final QueueReleaseInterface queueReleaseInterface
    ) throws Throwable {
        final QueueReleasingEmitterWrapper<Integer> emitterWrapper = new QueueReleasingEmitterWrapper<>(emitter, queueReleaseInterface);
        if (server == null) {
            RxBleLog.w("NotificationSendOperation encountered null gatt server");
            emitter.onError(new BleGattServerException(
                    BleGattServerOperationType.CONNECTION_STATE,
                    "server handle was null in NotifyCharacteristicChangedOperation"
                    )
            );
            emitterWrapper.cancel();
        } else if (characteristic.getService() == null) {
            emitterWrapper.onError(new BleGattServerException(
                    BleGattServerOperationType.NOTIFICATION_SENT,
                    "service for characteristic " + characteristic.getUuid() + " was null"
            ));
        } else {
            RxBleLog.d("running notifycharacteristic notification/indication operation device: ");


            getCompleted()
                    .timeout(
                            timeoutConfiguration.timeout,
                            timeoutConfiguration.timeoutTimeUnit,
                            timeoutConfiguration.timeoutScheduler
                    )
                    .toObservable()
                    .doOnComplete(new Action() {
                        @Override
                        public void run() throws Exception {
                            RxBleLog.d("completed notifycharacteristic operation");
                        }
                    })
                    .subscribe(emitterWrapper);

            characteristic.setValue(value);
            if (!server.notifyCharacteristicChanged(device.getBluetoothDevice(), characteristic, isIndication)) {
                emitterWrapper.onError(new BleGattServerException(
                        BleGattServerOperationType.CONNECTION_STATE,
                        "NotifyCharacteristicChangedOperation failed"
                ));
            }
        }
    }

    private Single<Integer> getCompleted() {
        return connection.getOnNotification()
                .firstOrError();
    }

    @Override
    protected BleException provideException(DeadObjectException deadObjectException) {
        return new BleGattServerException(
                BleGattServerOperationType.NOTIFICATION_SENT,
                "notification failed"
        );
    }
}
