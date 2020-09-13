package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.os.DeadObjectException;

import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.internal.QueueOperation;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.serialization.QueueReleaseInterface;
import com.polidea.rxandroidble2.internal.server.BluetoothGattServerProvider;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;

public abstract class NotifyCharacteristicChangedOperation extends QueueOperation<Integer> {

    private final BluetoothDevice device;
    private final BluetoothGattServerProvider serverProvider;
    private final Observable<Integer> notificationCompletedObservable;
    private final Scheduler clientScheduler;
    private final BluetoothGattCharacteristic characteristic;
    private final TimeoutConfiguration timeoutConfiguration;

    public NotifyCharacteristicChangedOperation(
            Scheduler clientScheduler,
            BluetoothDevice device,
            BluetoothGattServerProvider serverProvider,
            Observable<Integer> notificationCompletedObservable,
            BluetoothGattCharacteristic characteristic,
            TimeoutConfiguration timeoutConfiguration
            ) {
        this.clientScheduler = clientScheduler;
        this.device = device;
        this.serverProvider = serverProvider;
        this.notificationCompletedObservable = notificationCompletedObservable;
        this.characteristic = characteristic;
        this.timeoutConfiguration = timeoutConfiguration;
    }


    @Override
    protected void protectedRun(final ObservableEmitter<Integer> emitter, QueueReleaseInterface queueReleaseInterface) throws Throwable {
        final BluetoothGattServer server = serverProvider.getBluetoothGatt();
        if (server == null) {
            RxBleLog.w("NotificationSendOperation encountered null gatt server");
            queueReleaseInterface.release();
            emitter.onComplete();
        } else {
            server.notifyCharacteristicChanged(device, characteristic, isIndication());
            getCompleted()
                    .timeout(
                            timeoutConfiguration.timeout,
                            timeoutConfiguration.timeoutTimeUnit,
                            timeoutConfiguration.timeoutScheduler
                    )
                    .observeOn(clientScheduler)
                    .subscribe(new SingleObserver<Integer>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                            // not used
                        }

                        @Override
                        public void onSuccess(Integer integer) {
                            emitter.onNext(integer);
                        }

                        @Override
                        public void onError(Throwable e) {
                            RxBleLog.w("onNotificationSent observable completed without returning items");
                            emitter.onError(e);
                        }
                    });

        }
    }

    public abstract boolean isIndication();

    private Single<Integer> getCompleted() {
        return notificationCompletedObservable.firstOrError();
    }

    @Override
    protected BleException provideException(DeadObjectException deadObjectException) {
        return null;
    }
}
