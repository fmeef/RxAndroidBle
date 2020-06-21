package com.polidea.rxandroidble2.internal.util;


import androidx.annotation.NonNull;

import com.polidea.rxandroidble2.ClientComponent;
import com.polidea.rxandroidble2.RxBleAdapterStateObservable;
import com.polidea.rxandroidble2.RxBleBase;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposables;
import io.reactivex.functions.Function;

/**
 * The Observable class which emits changes to the Server State. These can be useful for evaluating if particular functionality
 * of the library has a chance to work properly.
 * <p>
 * For more info check {@link RxBleBase.State}
 */
public class ServerStateObservable extends Observable<RxBleBase.State> {

    final RxBleAdapterWrapper rxBleAdapterWrapper;
    final Observable<RxBleAdapterStateObservable.BleAdapterState> bleAdapterStateObservable;
    private final Scheduler timerScheduler;

    @Inject
    protected ServerStateObservable(
            final RxBleAdapterWrapper rxBleAdapterWrapper,
            final Observable<RxBleAdapterStateObservable.BleAdapterState> bleAdapterStateObservable,
            @Named(ClientComponent.NamedSchedulers.TIMEOUT) final Scheduler timerScheduler
    ) {
        this.rxBleAdapterWrapper = rxBleAdapterWrapper;
        this.bleAdapterStateObservable = bleAdapterStateObservable;
        this.timerScheduler = timerScheduler;
    }

    @NonNull
    static Observable<RxBleBase.State> checkAdapterAndServicesState(
            RxBleAdapterWrapper rxBleAdapterWrapper,
            Observable<RxBleAdapterStateObservable.BleAdapterState> rxBleAdapterStateObservable

    ) {
        return rxBleAdapterStateObservable
                .startWith(rxBleAdapterWrapper.isBluetoothEnabled()
                        ? RxBleAdapterStateObservable.BleAdapterState.STATE_ON
                        /*
                         * Actual RxBleAdapterStateObservable.BleAdapterState does not really matter - because in the .switchMap() below
                         * we only check if it is STATE_ON or not
                         */
                        : RxBleAdapterStateObservable.BleAdapterState.STATE_OFF)
                .switchMap(new Function<RxBleAdapterStateObservable.BleAdapterState, Observable<RxBleBase.State>>() {
                    @Override
                    public Observable<RxBleBase.State> apply(
                            RxBleAdapterStateObservable.BleAdapterState bleAdapterState) {
                        if (bleAdapterState != RxBleAdapterStateObservable.BleAdapterState.STATE_ON) {
                            return Observable.just(RxBleBase.State.BLUETOOTH_NOT_ENABLED);
                        } else {
                            return Observable.just(RxBleBase.State.READY);
                        }
                    }
                });
    }

    @Override
    protected void subscribeActual(Observer<? super RxBleBase.State> observer) {
        if (!rxBleAdapterWrapper.hasBluetoothAdapter()) {
            observer.onSubscribe(Disposables.empty());
            observer.onComplete();
        }
    }
}
