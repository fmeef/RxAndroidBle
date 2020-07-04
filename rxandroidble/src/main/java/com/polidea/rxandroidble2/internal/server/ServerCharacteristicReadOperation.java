package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothGatt;
import android.os.DeadObjectException;

import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.exceptions.BleGattOperationType;
import com.polidea.rxandroidble2.internal.SingleResponseOperation;
import com.polidea.rxandroidble2.internal.connection.RxBleGattCallback;
import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;

import io.reactivex.Scheduler;
import io.reactivex.Single;

public class ServerCharacteristicReadOperation extends SingleResponseOperation<byte[]> {
    public ServerCharacteristicReadOperation(BluetoothGatt bluetoothGatt,
                                             RxBleGattCallback rxBleGattCallback,
                                             BleGattOperationType gattOperationType,
                                             TimeoutConfiguration timeoutConfiguration) {
        super(bluetoothGatt, rxBleGattCallback, gattOperationType, timeoutConfiguration);
    }

    @Override
    protected Single<byte[]> getCallback(RxBleGattCallback rxBleGattCallback) {
        return null;
    }

    @Override
    protected boolean startOperation(BluetoothGatt bluetoothGatt) {
        return false;
    }

    @Override
    protected Single<byte[]> timeoutFallbackProcedure(BluetoothGatt bluetoothGatt,
                                                      RxBleGattCallback rxBleGattCallback,
                                                      Scheduler timeoutScheduler) {
        return super.timeoutFallbackProcedure(bluetoothGatt, rxBleGattCallback, timeoutScheduler);
    }

    @Override
    protected BleException provideException(DeadObjectException deadObjectException) {
        return super.provideException(deadObjectException);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
