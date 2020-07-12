package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.exceptions.BleScanException;
import com.polidea.rxandroidble2.internal.connection.ServerConnector;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnection;
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

public class RxBleServerImpl extends RxBleServer {
    @Deprecated
    public static final String TAG = "RxBleCglient";
    final Scheduler bluetoothInteractionScheduler;
    private final RxBleAdapterWrapper rxBleAdapterWrapper;
    private final ServerComponent.ServerComponentFinalizer serverComponentFinalizer;
    private final Observable<RxBleAdapterStateObservable.BleAdapterState> rxBleAdapterStateObservable;
    private final Lazy<ServerStateObservable> lazyServerStateObservable;
    private final ServerConnector serverConnector;

    @Inject
    public RxBleServerImpl(
            @Named(ServerComponent.NamedSchedulers.BLUETOOTH_INTERACTION) final Scheduler bluetoothInteractionScheduler,
            final RxBleAdapterWrapper rxBleAdapterWrapper,
            final Observable<RxBleAdapterStateObservable.BleAdapterState> rxBleAdapterStateObservable,
            final ServerComponent.ServerComponentFinalizer serverComponentFinalizer,
            final Lazy<ServerStateObservable> lazyServerStateObservable,
            final ServerConnector serverConnector
    ) {
        this.bluetoothInteractionScheduler = bluetoothInteractionScheduler;
        this.rxBleAdapterWrapper = rxBleAdapterWrapper;
        this.rxBleAdapterStateObservable = rxBleAdapterStateObservable;
        this.serverComponentFinalizer = serverComponentFinalizer;
        this.lazyServerStateObservable = lazyServerStateObservable;
        this.serverConnector = serverConnector;

    }

    public Observable<Set<BluetoothDevice>> getConnectedDevices() {
        return null;
    }

    @Override
    protected void finalize() throws Throwable {
        serverComponentFinalizer.onFinalize();
        super.finalize();
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

    @Override
    public Observable<RxBleServerConnection> openServer() {
        return serverConnector.subscribeToConnections(); //TODO:
    }

    @Override
    public void closeServer() {

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
