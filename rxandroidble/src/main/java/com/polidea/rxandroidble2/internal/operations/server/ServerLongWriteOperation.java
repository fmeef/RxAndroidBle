package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;
import android.os.DeadObjectException;

import com.polidea.rxandroidble2.ServerConnectionComponent;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.exceptions.BleGattServerOperationType;
import com.polidea.rxandroidble2.internal.QueueOperation;
import com.polidea.rxandroidble2.internal.serialization.QueueReleaseInterface;
import com.polidea.rxandroidble2.internal.util.QueueReleasingEmitterWrapper;

import java.util.Arrays;

import bleshadow.javax.inject.Named;
import io.reactivex.MaybeObserver;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;

public class ServerLongWriteOperation extends QueueOperation<byte[]> {
    private final Scheduler bluetoothInteractionScheduler;
    private final Observable<byte[]> inputBytesObservable;
    private final BluetoothDevice endpointDevice;


    public ServerLongWriteOperation(
            @Named(ServerConnectionComponent.NamedSchedulers.BLUETOOTH_INTERACTION) Scheduler bluetoothInteractionScheduler,
            Observable<byte[]> inputBytesObservable,
            BluetoothDevice endpointDevice
            ) {
        this.bluetoothInteractionScheduler = bluetoothInteractionScheduler;
        this.inputBytesObservable = inputBytesObservable;
        this.endpointDevice = endpointDevice;
    }

    @Override
    protected void protectedRun(final ObservableEmitter<byte[]> emitter, QueueReleaseInterface queueReleaseInterface) throws Throwable {
        final QueueReleasingEmitterWrapper<byte[]> emitterWrapper = new QueueReleasingEmitterWrapper<>(emitter, queueReleaseInterface);
        inputBytesObservable
                .reduce(new BiFunction<byte[], byte[], byte[]>() {
                    @Override
                    public byte[] apply(byte[] first, byte[] second) throws Exception {
                        byte[] both = Arrays.copyOf(first, first.length + second.length);
                        System.arraycopy(second, 0, both, first.length, second.length);
                        return both;
                    }
                })
                .subscribeOn(bluetoothInteractionScheduler)
                .subscribe(new MaybeObserver<byte[]>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        // not used
                    }

                    @Override
                    public void onSuccess(byte[] bytes) {
                        emitterWrapper.onNext(bytes);
                    }

                    @Override
                    public void onError(Throwable e) {
                        emitterWrapper.onError(e);
                    }

                    @Override
                    public void onComplete() {
                        emitterWrapper.onComplete();
                    }
                });
    }

    @Override
    protected BleGattServerException provideException(DeadObjectException deadObjectException) {
        return new BleGattServerException(-1, endpointDevice, BleGattServerOperationType.CHARACTERISTIC_LONG_WRITE_REQUEST);
    }
}
