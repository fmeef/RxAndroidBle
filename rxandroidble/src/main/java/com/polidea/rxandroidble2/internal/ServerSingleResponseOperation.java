package com.polidea.rxandroidble2.internal;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattServer;
import android.os.DeadObjectException;

import androidx.annotation.NonNull;

import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.exceptions.BleGattCallbackTimeoutException;
import com.polidea.rxandroidble2.exceptions.BleGattServerCallbackTimeoutException;
import com.polidea.rxandroidble2.exceptions.BleGattServerCannotStartException;
import com.polidea.rxandroidble2.exceptions.BleGattServerOperationType;
import com.polidea.rxandroidble2.internal.logger.LoggerUtil;
import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.serialization.QueueReleaseInterface;
import com.polidea.rxandroidble2.internal.server.RxBleGattServerCallback;
import com.polidea.rxandroidble2.internal.util.QueueReleasingEmitterWrapper;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.Scheduler;
import io.reactivex.Single;

public abstract class ServerSingleResponseOperation<T> extends QueueOperation<T> {
    private final RxBleGattServerCallback rxBleGattServerCallback;
    private final BleGattServerOperationType operationType;
    private final TimeoutConfiguration timeoutConfiguration;
    private final BluetoothGattServer bluetoothGattServer;
    private final BluetoothDevice bluetoothDevice;

    public ServerSingleResponseOperation(BluetoothGattServer bluetoothGattServer,
                                         RxBleGattServerCallback rxBleGattServerCallback,
                                         BleGattServerOperationType gattOperationType,
                                         TimeoutConfiguration timeoutConfiguration,
                                         BluetoothDevice bluetoothDevice) {
        this.rxBleGattServerCallback = rxBleGattServerCallback;
        this.operationType = gattOperationType;
        this.timeoutConfiguration = timeoutConfiguration;
        this.bluetoothGattServer = bluetoothGattServer;
        this.bluetoothDevice = bluetoothDevice;
    }

    @Override
    protected void protectedRun(ObservableEmitter<T> emitter, QueueReleaseInterface queueReleaseInterface) throws Throwable {
        final QueueReleasingEmitterWrapper<T> emitterWrapper = new QueueReleasingEmitterWrapper<>(emitter, queueReleaseInterface);
        getCallback(rxBleGattServerCallback)
                .timeout(
                        timeoutConfiguration.timeout,
                        timeoutConfiguration.timeoutTimeUnit,
                        timeoutConfiguration.timeoutScheduler,
                        timeoutFallbackProcedure(bluetoothGattServer, rxBleGattServerCallback, timeoutConfiguration.timeoutScheduler)
                )
                .toObservable()
                .subscribe(emitterWrapper);

        if (!startOperation(bluetoothGattServer)) {
            emitterWrapper.cancel();
            emitterWrapper.onError(new BleGattServerCannotStartException(bluetoothGattServer, operationType));
        }
    }

    /**
     * A function that should return {@link Observable} derived from the passed {@link RxBleGattServerCallback}.
     * The returned {@link Observable} will be automatically unsubscribed after the first emission.
     * The returned {@link Observable} is a subject to {@link Observable#timeout(long, TimeUnit, Scheduler, io.reactivex.ObservableSource)}
     * and by default it will throw {@link BleGattCallbackTimeoutException}. This behaviour can be overridden by overriding
     * {@link #timeoutFallbackProcedure(BluetoothGattServer, RxBleGattServerCallback, Scheduler)}.
     *
     * @param rxBleGattCallback the {@link RxBleGattServerCallback} to use
     * @return the Observable
     */
    abstract protected Single<T> getCallback(RxBleGattServerCallback rxBleGattCallback);
    /**
     * A function that should call the passed {@link BluetoothGatt} and return `true` if the call has succeeded.
     *
     * @param bluetoothGattServer the {@link BluetoothGattServer} to use
     * @return `true` if success, `false` otherwise
     */
    abstract protected boolean startOperation(BluetoothGattServer bluetoothGattServer);



    @SuppressWarnings("unused")
    protected Single<T> timeoutFallbackProcedure(
            BluetoothGattServer bluetoothGatts,
            RxBleGattServerCallback rxBleGattCallback,
            Scheduler timeoutScheduler
    ) {
        return Single.error(new BleGattServerCallbackTimeoutException(this.bluetoothGattServer, null, operationType));
    }

    @Override
    protected BleException provideException(DeadObjectException deadObjectException) {
        return new BleDisconnectedException(deadObjectException, bluetoothDevice.getAddress(),
                BleDisconnectedException.UNKNOWN_STATUS);
    }

    @NonNull
    @Override
    public String toString() {
        return LoggerUtil.commonMacMessage(bluetoothDevice.getAddress());
    }
}
