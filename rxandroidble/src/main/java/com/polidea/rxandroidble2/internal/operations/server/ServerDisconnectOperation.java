package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.os.DeadObjectException;
import android.util.Pair;

import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.internal.QueueOperation;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.serialization.QueueReleaseInterface;
import com.polidea.rxandroidble2.internal.server.BluetoothGattServerProvider;
import com.polidea.rxandroidble2.internal.server.RxBleGattServerCallback;

import io.reactivex.ObservableEmitter;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;

public class ServerDisconnectOperation extends QueueOperation<Void> {

    private final BluetoothGattServerProvider provider;
    private final BluetoothDevice device;
    private final RxBleGattServerCallback callback;
    private final Scheduler gattServerScheduler;
    private final BluetoothManager bluetoothManager;
    private final TimeoutConfiguration timeoutConfiguration;

    ServerDisconnectOperation(
            BluetoothGattServerProvider provider,
            BluetoothDevice device,
            RxBleGattServerCallback callback,
            Scheduler gattServerScheduler,
            BluetoothManager bluetoothManager,
            TimeoutConfiguration timeoutConfiguration
    ) {
        this.provider = provider;
        this.device = device;
        this.callback = callback;
        this.gattServerScheduler = gattServerScheduler;
        this.bluetoothManager = bluetoothManager;
        this.timeoutConfiguration = timeoutConfiguration;
    }

    @Override
    protected void protectedRun(final ObservableEmitter<Void> emitter, final QueueReleaseInterface queueReleaseInterface) throws Throwable {
        final BluetoothGattServer bluetoothGattServer = provider.getServer();
        if (bluetoothGattServer == null) {
            RxBleLog.w("Server disconnect operation on client " + device.getAddress() + " with null BluetoothGattServer");
            queueReleaseInterface.release();
            emitter.onComplete();
        } else {
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
    }

    private Single<BluetoothGattServer> disconnectIfRequired() {
        return isDisconnected()
                ? Single.just(provider.getServer())
                : disconnect();
    }

    private Single<BluetoothGattServer> disconnect() {
        //TODO: handle timeout
        return new DisconnectGattServerObservable(
                provider.getServer(),
                callback,
                gattServerScheduler,
                device
                );
    }

    private boolean isDisconnected() {
        return bluetoothManager
                .getConnectionState(device, BluetoothProfile.GATT) == BluetoothProfile.STATE_DISCONNECTED;
    }

    @Override
    protected BleException provideException(DeadObjectException deadObjectException) {
        return new BleDisconnectedException(deadObjectException, device.getAddress(), BleDisconnectedException.UNKNOWN_STATUS);
    }

    public static class DisconnectGattServerObservable extends Single<BluetoothGattServer> {
        private final BluetoothGattServer bluetoothGattServer;
        private final RxBleGattServerCallback callback;
        private final Scheduler disconnectScheduler;
        private final BluetoothDevice device;

        public DisconnectGattServerObservable(
                BluetoothGattServer bluetoothGattServer,
                RxBleGattServerCallback callback,
                Scheduler disconnectScheduler,
                BluetoothDevice device
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
                    .filter(new Predicate<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>>() {
                        @Override
                        public boolean test(Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState> pair) throws Exception {
                            return pair.first.equals(device) && pair.second == RxBleConnection.RxBleConnectionState.DISCONNECTED;
                        }
                    })
                    .firstOrError()
                    .map(new Function<Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState>, BluetoothGattServer>() {
                        @Override
                        public BluetoothGattServer apply(
                                Pair<BluetoothDevice, RxBleConnection.RxBleConnectionState> pair
                        ) throws Exception {
                            return bluetoothGattServer;
                        }
                    })
                    .subscribe(observer);

            disconnectScheduler.createWorker().schedule(new Runnable() {
                @Override
                public void run() {
                    bluetoothGattServer.cancelConnection(device);
                }
            });
        }
    }
}
