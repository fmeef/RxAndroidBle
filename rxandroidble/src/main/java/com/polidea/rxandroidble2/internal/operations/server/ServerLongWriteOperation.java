package com.polidea.rxandroidble2.internal.operations.server;

import android.os.DeadObjectException;

import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.exceptions.BleException;
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


    public ServerLongWriteOperation(
            @Named(ServerComponent.NamedSchedulers.BLUETOOTH_INTERACTION) Scheduler bluetoothInteractionScheduler,
            Observable<byte[]> inputBytesObservable
            ) {
        this.bluetoothInteractionScheduler = bluetoothInteractionScheduler;
        this.inputBytesObservable = inputBytesObservable;
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
    protected BleException provideException(DeadObjectException deadObjectException) {
        return null;
    }
}
