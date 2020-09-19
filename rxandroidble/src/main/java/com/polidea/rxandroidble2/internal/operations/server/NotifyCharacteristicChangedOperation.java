package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.os.DeadObjectException;
import android.util.Log;

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

public abstract class NotifyCharacteristicChangedOperation extends QueueOperation<Integer> {

    private final BluetoothGattServerProvider serverProvider;
    private final BluetoothGattCharacteristic characteristic;
    private final TimeoutConfiguration timeoutConfiguration;
    private final RxBleServerConnectionInternal connection;
    private final byte[] value;

    public NotifyCharacteristicChangedOperation(
            BluetoothGattServerProvider serverProvider,
            BluetoothGattCharacteristic characteristic,
            TimeoutConfiguration timeoutConfiguration,
            RxBleServerConnectionInternal connection,
            byte[] value
            ) {
        this.serverProvider = serverProvider;
        this.characteristic = characteristic;
        this.timeoutConfiguration = timeoutConfiguration;
        this.connection = connection;
        this.value = value;
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
            emitterWrapper.cancel();
            emitter.onError(new BleGattServerException(server, connection.getDevice(), BleGattServerOperationType.CONNECTION_STATE));
        } else {
            Log.v("debug", "NotifyCharacteristicChanged");
            getCompleted()
                    .timeout(
                            timeoutConfiguration.timeout,
                            timeoutConfiguration.timeoutTimeUnit,
                            timeoutConfiguration.timeoutScheduler
                    )
                    .toObservable()
                    .subscribe(emitterWrapper);

            characteristic.setValue(value);
            if (!server.notifyCharacteristicChanged(connection.getDevice(), characteristic, isIndication())) {
                emitterWrapper.cancel();
                emitter.onError(new BleGattServerException(server, connection.getDevice(), BleGattServerOperationType.CONNECTION_STATE));
            }
        }
    }

    public abstract boolean isIndication();

    private Single<Integer> getCompleted() {
        return connection.getOnNotification().firstOrError();
    }

    @Override
    protected BleException provideException(DeadObjectException deadObjectException) {
        return null;
    }
}
