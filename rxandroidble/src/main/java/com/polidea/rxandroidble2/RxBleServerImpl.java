package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import androidx.annotation.NonNull;

import com.polidea.rxandroidble2.exceptions.BleScanException;
import com.polidea.rxandroidble2.internal.RxBleDeviceProvider;
import com.polidea.rxandroidble2.internal.serialization.ClientOperationQueue;
import com.polidea.rxandroidble2.internal.util.RxBleAdapterWrapper;
import com.polidea.rxandroidble2.internal.util.ServerStateObservable;

import java.util.Set;

import bleshadow.dagger.Lazy;
import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Maybe;
import io.reactivex.MaybeSource;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.subjects.PublishSubject;

public class RxBleServerImpl extends RxBleServer {
    @Deprecated
    public static final String TAG = "RxBleClient";
    final ClientOperationQueue operationQueue;
    private final RxBleDeviceProvider rxBleDeviceProvider;
    final Scheduler bluetoothInteractionScheduler;
    private final RxBleAdapterWrapper rxBleAdapterWrapper;
    private final ServerComponent.ServerComponentFinalizer serverComponentFinalizer;
    private final Observable<RxBleAdapterStateObservable.BleAdapterState> rxBleAdapterStateObservable;
    private final Lazy<ServerStateObservable> lazyServerStateObservable;
    private final BluetoothManager bluetoothManager;

    private BluetoothGattServer gattServer;
    private final PublishSubject<Set<BluetoothDevice>> bluetoothDeviceChangedSubject;

    @Inject
    public RxBleServerImpl(
            final ClientOperationQueue operationQueue,
            final RxBleDeviceProvider rxBleDeviceProvider,
            @Named(ClientComponent.NamedSchedulers.BLUETOOTH_INTERACTION) final Scheduler bluetoothInteractionScheduler,
            final RxBleAdapterWrapper rxBleAdapterWrapper,
            final Observable<RxBleAdapterStateObservable.BleAdapterState> rxBleAdapterStateObservable,
            final ServerComponent.ServerComponentFinalizer serverComponentFinalizer,
            final Lazy<ServerStateObservable> lazyServerStateObservable,
            final BluetoothManager bluetoothManager
    ) {
        this.operationQueue = operationQueue;
        this.rxBleDeviceProvider = rxBleDeviceProvider;
        this.bluetoothInteractionScheduler = bluetoothInteractionScheduler;
        this.rxBleAdapterWrapper = rxBleAdapterWrapper;
        this.rxBleAdapterStateObservable = rxBleAdapterStateObservable;
        this.serverComponentFinalizer = serverComponentFinalizer;
        this.lazyServerStateObservable = lazyServerStateObservable;
        this.bluetoothManager = bluetoothManager;
        this.gattServer = null;
        this.bluetoothDeviceChangedSubject = PublishSubject.create();
    }

    public Observable<Set<BluetoothDevice>> getConnectedDevices() {
        return this.bluetoothDeviceChangedSubject;
    }

    public boolean openGattServer(Context context) {
        if (this.gattServer != null) {
            return false;
        }
        //this.gattServer = bluetoothManager.openGattServer(context, gattServerCallback); TODO
        return this.gattServer != null;
    }

    public void closeGattServer() {
        this.gattServer.clearServices();
        this.gattServer.close();
    }

    @Override
    protected void finalize() throws Throwable {
        serverComponentFinalizer.onFinalize();
        super.finalize();
    }

    @Override
    public RxBleDevice getBleDevice(@NonNull String macAddress) {
        guardBluetoothAdapterAvailable();
        return rxBleDeviceProvider.getBleDevice(macAddress);
    }


    @Override
    public Observable<RxBleBase.State> observeStateChanges() {
        return lazyServerStateObservable.get();
    }

    @Override
    public State getState() {
        if (!rxBleAdapterWrapper.hasBluetoothAdapter()) {
            return State.BLUETOOTH_NOT_AVAILABLE;
        }
        if (!rxBleAdapterWrapper.isBluetoothEnabled()) {
            return State.BLUETOOTH_NOT_ENABLED;
        } else {
            return State.READY;
        }
    }

    private void guardBluetoothAdapterAvailable() {
        if (!rxBleAdapterWrapper.hasBluetoothAdapter()) {
            throw new UnsupportedOperationException("RxAndroidBle library needs a BluetoothAdapter to be available in the system to work."
            + " If this is a test on an emulator then you can use 'https://github.com/Polidea/RxAndroidBle/tree/master/mockrxandroidble'");

        }
    }

    /**
     * This {@link Observable} will not emit values by design. It may only emit {@link BleScanException} if
     * bluetooth adapter is turned down.
     */
    <T> Observable<T> bluetoothAdapterOffExceptionObservable() {
        return rxBleAdapterStateObservable
                .filter(new Predicate<RxBleAdapterStateObservable.BleAdapterState>() {
                    @Override
                    public boolean test(RxBleAdapterStateObservable.BleAdapterState state) {
                        return state != RxBleAdapterStateObservable.BleAdapterState.STATE_ON;
                    }
                })
                .firstElement()
                .flatMap(new Function<RxBleAdapterStateObservable.BleAdapterState, MaybeSource<T>>() {
                    @Override
                    public MaybeSource<T> apply(RxBleAdapterStateObservable.BleAdapterState bleAdapterState) {
                        return Maybe.error(new BleScanException(BleScanException.BLUETOOTH_DISABLED));
                    }
                })
                .toObservable();
    }
}
