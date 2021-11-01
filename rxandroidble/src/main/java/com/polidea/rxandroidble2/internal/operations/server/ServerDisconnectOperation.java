package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.os.DeadObjectException;
import android.util.Pair;

import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.internal.QueueOperation;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.serialization.QueueReleaseInterface;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternal;

import io.reactivex.ObservableEmitter;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;

public class ServerDisconnectOperation extends QueueOperation<Void> {

    private final BluetoothGattServer server;
    private final RxBleDevice device;
    private final RxBleServerConnectionInternal callback;
    private final Scheduler gattServerScheduler;
    private final BluetoothManager bluetoothManager;
    private final TimeoutConfiguration timeoutConfiguration;

    ServerDisconnectOperation(
            BluetoothGattServer server,
            RxBleDevice device,
            RxBleServerConnectionInternal callback,
            Scheduler gattServerScheduler,
            BluetoothManager bluetoothManager,
            TimeoutConfiguration timeoutConfiguration
    ) {
        this.server = server;
        this.device = device;
        this.callback = callback;
        this.gattServerScheduler = gattServerScheduler;
        this.bluetoothManager = bluetoothManager;
        this.timeoutConfiguration = timeoutConfiguration;
    }

    @Override
    protected void protectedRun(final ObservableEmitter<Void> emitter, final QueueReleaseInterface queueReleaseInterface) {
            disconnectIfRequired()
             .timeout(
                     timeoutConfiguration.timeout,
                     timeoutConfiguration.timeoutTimeUnit,
                     timeoutConfiguration.timeoutScheduler
             )
            .observeOn(gattServerScheduler)
            .subscribe(new SingleObserver<BluetoothGattServer>() {
                @Override
                public void onSubscribe(Disposable d) {
                    //not used
                }

                @Override
                public void onSuccess(BluetoothGattServer bluetoothGattServer) {
                    queueReleaseInterface.release();
                    emitter.onComplete();
                }

                @Override
                public void onError(Throwable e) {
                    RxBleLog.w(
                            e,
                            "Server Disconnect Operation has been executed, but finished with error"
                    );
                    queueReleaseInterface.release();
                    emitter.onComplete();
                }
            });
    }

    private Single<BluetoothGattServer> disconnectIfRequired() {
        return isDisconnected()
                ? Single.just(server)
                : disconnect();
    }

    private Single<BluetoothGattServer> disconnect() {
        //TODO: handle timeout
        return new DisconnectGattServerObservable(
                server,
                callback,
                gattServerScheduler,
                device
                );
    }

    private boolean isDisconnected() {
        return bluetoothManager
                .getConnectionState(device.getBluetoothDevice(), BluetoothProfile.GATT) == BluetoothProfile.STATE_DISCONNECTED;
    }

    @Override
    protected BleException provideException(DeadObjectException deadObjectException) {
        return new BleDisconnectedException(deadObjectException, device.getMacAddress(), BleDisconnectedException.UNKNOWN_STATUS);
    }

    public static class DisconnectGattServerObservable extends Single<BluetoothGattServer> {
        private final BluetoothGattServer bluetoothGattServer;
        private final RxBleServerConnectionInternal callback;
        private final Scheduler disconnectScheduler;
        private final RxBleDevice device;

        public DisconnectGattServerObservable(
                BluetoothGattServer bluetoothGattServer,
                RxBleServerConnectionInternal callback,
                Scheduler disconnectScheduler,
                RxBleDevice device
        ) {
            this.bluetoothGattServer = bluetoothGattServer;
            this.disconnectScheduler = disconnectScheduler;
            this.callback = callback;
            this.device = device;
        }


        @Override
        protected void subscribeActual(SingleObserver<? super BluetoothGattServer> observer) {
            callback
                    .getOnConnectionStateChange()
                    .filter(new Predicate<Pair<RxBleDevice, RxBleConnection.RxBleConnectionState>>() {
                        @Override
                        public boolean test(Pair<RxBleDevice, RxBleConnection.RxBleConnectionState> pair) {
                            return pair.first.equals(device) && pair.second == RxBleConnection.RxBleConnectionState.DISCONNECTED;
                        }
                    })
                    .firstOrError()
                    .map(new Function<Pair<RxBleDevice, RxBleConnection.RxBleConnectionState>, BluetoothGattServer>() {
                        @Override
                        public BluetoothGattServer apply(
                                Pair<RxBleDevice, RxBleConnection.RxBleConnectionState> pair
                        ) {
                            return bluetoothGattServer;
                        }
                    })
                    .subscribe(observer);

            disconnectScheduler.createWorker().schedule(new Runnable() {
                @Override
                public void run() {
                    bluetoothGattServer.cancelConnection(device.getBluetoothDevice());
                }
            });
        }
    }
}
