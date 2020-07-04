package com.polidea.rxandroidble2.internal;

import android.bluetooth.BluetoothGatt;
import android.os.DeadObjectException;

import androidx.annotation.NonNull;

import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.exceptions.BleGattOperationType;
import com.polidea.rxandroidble2.internal.operations.Operation;
import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.serialization.QueueReleaseInterface;
import com.polidea.rxandroidble2.internal.server.RxBleGattServerCallback;

import io.reactivex.ObservableEmitter;

public class ServerSingleResponseOperation<T> extends QueueOperation<T> {
    private final RxBleGattServerCallback rxBleGattServerCallback;
    private final BleGattOperationType operationType;
    private final TimeoutConfiguration timeoutConfiguration;

    public ServerSingleResponseOperation(BluetoothGatt bluetoothGatt,
                                   RxBleGattServerCallback rxBleGattServerCallback,
                                   BleGattOperationType gattOperationType,
                                   TimeoutConfiguration timeoutConfiguration) {
        this.rxBleGattServerCallback = rxBleGattServerCallback;
        this.operationType = gattOperationType;
        this.timeoutConfiguration = timeoutConfiguration;
    }

    @Override
    protected void protectedRun(ObservableEmitter<T> emitter, QueueReleaseInterface queueReleaseInterface) throws Throwable {

    }

    @Override
    protected BleException provideException(DeadObjectException deadObjectException) {
        return null;
    }

    @Override
    public Priority definedPriority() {
        return super.definedPriority();
    }

    @Override
    public int compareTo(@NonNull Operation another) {
        return super.compareTo(another);
    }
}
