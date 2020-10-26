package com.polidea.rxandroidble2.internal.server;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.polidea.rxandroidble2.RxBleAdapterStateObservable;
import com.polidea.rxandroidble2.ServerScope;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.connection.DisconnectionRouterInput;
import com.polidea.rxandroidble2.internal.connection.DisconnectionRouterOutput;
import com.polidea.rxandroidble2.internal.util.RxBleAdapterWrapper;

import bleshadow.javax.inject.Inject;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;

@ServerScope
public class ServerDisconnectionRouter implements DisconnectionRouterInput<BleGattServerException>, DisconnectionRouterOutput {
    private final BehaviorRelay<BleException> bleExceptionBehaviorRelay = BehaviorRelay.create();
    private final Observable<BleException> firstDisconnectionValueObs;
    private final Observable<Object> firstDisconnectionExceptionObs;

    @Inject
    ServerDisconnectionRouter(
            final RxBleAdapterWrapper adapterWrapper,
            final Observable<RxBleAdapterStateObservable.BleAdapterState> adapterStateObservable
            ) {
        final Disposable adapterMonitoringDisposable = awaitAdapterNotUsable(adapterWrapper, adapterStateObservable)
                .map(new Function<Boolean, BleException>() {
                    @Override
                    public BleException apply(Boolean isAdapterUsable) {
                        return BleDisconnectedException.adapterDisabled("changethis");
                    }
                })
                .doOnNext(new Consumer<BleException>() {
                    @Override
                    public void accept(BleException exception) {
                        RxBleLog.v("An exception received, indicating that the adapter has became unusable.");
                    }
                })
                .subscribe(bleExceptionBehaviorRelay, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        RxBleLog.e(throwable, "Failed to monitor adapter state.");
                    }
                });
        firstDisconnectionValueObs = bleExceptionBehaviorRelay
                .firstElement()
                .toObservable()
                .doOnTerminate(new Action() {
                    @Override
                    public void run() throws Exception {
                        adapterMonitoringDisposable.dispose();
                    }
                })
                .replay()
                .autoConnect(0);

        firstDisconnectionExceptionObs = firstDisconnectionValueObs
                .flatMap(new Function<BleException, ObservableSource<?>>() {
                    @Override
                    public ObservableSource<?> apply(BleException e) throws Exception {
                        return Observable.error(e);
                    }
                });
    }

    private static Observable<Boolean> awaitAdapterNotUsable(RxBleAdapterWrapper adapterWrapper,
                                                             Observable<RxBleAdapterStateObservable.BleAdapterState> stateChanges) {
        return stateChanges
                .map(new Function<RxBleAdapterStateObservable.BleAdapterState, Boolean>() {
                    @Override
                    public Boolean apply(RxBleAdapterStateObservable.BleAdapterState bleAdapterState) {
                        return bleAdapterState.isUsable();
                    }
                })
                .startWith(adapterWrapper.isBluetoothEnabled())
                .filter(new Predicate<Boolean>() {
                    @Override
                    public boolean test(Boolean isAdapterUsable) {
                        return !isAdapterUsable;
                    }
                });
    }


    @Override
    public void onDisconnectedException(BleDisconnectedException disconnectedException) {
        bleExceptionBehaviorRelay.accept(disconnectedException);
    }

    @Override
    public void onGattConnectionStateException(BleGattServerException disconnectedGattException) {
        bleExceptionBehaviorRelay.accept(disconnectedGattException);
    }

    @Override
    public Observable<BleException> asValueOnlyObservable() {
        return firstDisconnectionValueObs;
    }

    @Override
    public <T> Observable<T> asErrorOnlyObservable() {
        //noinspection unchecked
        return (Observable<T>) firstDisconnectionExceptionObs;
    }
}
