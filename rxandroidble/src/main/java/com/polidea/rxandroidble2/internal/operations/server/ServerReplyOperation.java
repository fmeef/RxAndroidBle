package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;
import android.os.DeadObjectException;

import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.exceptions.BleGattServerOperationType;
import com.polidea.rxandroidble2.internal.QueueOperation;
import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.serialization.QueueReleaseInterface;
import com.polidea.rxandroidble2.internal.util.QueueReleasingEmitterWrapper;

import java.util.concurrent.Callable;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;

public class ServerReplyOperation extends QueueOperation<Boolean> {
    private final BluetoothGattServer bluetoothGattServer;
    private final int requestID;
    private final int offset;
    private final byte[] value;
    private final int status;
    private final BluetoothDevice device;
    private final TimeoutConfiguration timeoutConfiguration;

    public ServerReplyOperation(
            TimeoutConfiguration timeoutConfiguration,
            BluetoothGattServer bluetoothGattServer,
            BluetoothDevice device,
            int requestID,
            int status,
            int offset,
            byte[] value
            ) {
        this.bluetoothGattServer = bluetoothGattServer;
        this.requestID = requestID;
        this.offset = offset;
        this.value = value;
        this.device = device;
        this.status = status;
        this.timeoutConfiguration = timeoutConfiguration;
    }

    @Override
    protected void protectedRun(ObservableEmitter<Boolean> emitter, QueueReleaseInterface queueReleaseInterface) throws Throwable {

        final QueueReleasingEmitterWrapper<Boolean> emitterWrapper = new QueueReleasingEmitterWrapper<>(emitter, queueReleaseInterface);
        Observable.fromCallable(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return bluetoothGattServer.sendResponse(device, requestID, status, offset, value);
            }
        }).subscribe(emitterWrapper);
    }

    @Override
    protected BleException provideException(DeadObjectException deadObjectException) {
        return new BleGattServerException(BleGattServerOperationType.REPLY, "ServerReplyOperation failed");
    }
}
