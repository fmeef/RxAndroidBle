package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothDevice;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.jakewharton.rxrelay2.PublishRelay;
import com.polidea.rxandroidble2.RxBleAdapterStateObservable;
import com.polidea.rxandroidble2.ServerConnectionScope;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.exceptions.BleGattServerException;
import com.polidea.rxandroidble2.internal.RxBleLog;
import com.polidea.rxandroidble2.internal.connection.DisconnectionRouterOutput;
import com.polidea.rxandroidble2.internal.util.RxBleAdapterWrapper;

import java.util.concurrent.ConcurrentHashMap;

import bleshadow.javax.inject.Inject;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;

@ServerConnectionScope
public class ServerDisconnectionRouter implements DisconnectionRouterOutput {
    private final ConcurrentHashMap<String, Pair<BehaviorRelay<BleException>, Disposable>> connectionMap = new ConcurrentHashMap<>();
    private final PublishRelay<BleException> firstDisconnectionValueObs = PublishRelay.create();
    private final PublishRelay<Object> firstDisconnectionExceptionObs = PublishRelay.create();
    private final RxBleAdapterWrapper adapterWrapper;
    private final Observable<RxBleAdapterStateObservable.BleAdapterState> adapterStateObservable;

    @Inject
    ServerDisconnectionRouter(
            final RxBleAdapterWrapper adapterWrapper,
            final Observable<RxBleAdapterStateObservable.BleAdapterState> adapterStateObservable
            ) {
        this.adapterStateObservable = adapterStateObservable;
        this.adapterWrapper = adapterWrapper;
    }

    private void addDevice(BluetoothDevice device) {
        final CompositeDisposable disp = new CompositeDisposable();
        final BehaviorRelay<BleException> relay = BehaviorRelay.create();
        final Disposable adapterMonitoringDisposable = awaitAdapterNotUsable(adapterWrapper, adapterStateObservable)
                .map(new Function<Boolean, BleException>() {
                    @Override
                    public BleException apply(Boolean isAdapterUsable) {
                        return BleDisconnectedException.adapterDisabled("BLE adapter disabled");
                    }
                })
                .doOnNext(new Consumer<BleException>() {
                    @Override
                    public void accept(BleException exception) {
                        RxBleLog.v("An exception received, indicating that the adapter has became unusable.");
                    }
                })
                .subscribe(relay, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        RxBleLog.e(throwable, "Failed to monitor adapter state.");
                    }
                });
        final Disposable valueDisp = relay
                .firstElement()
                .toObservable()
                .doOnTerminate(new Action() {
                    @Override
                    public void run() {
                        adapterMonitoringDisposable.dispose();
                    }
                })
                .replay()
                .autoConnect(0)
                .subscribe(firstDisconnectionValueObs);

        final Disposable exceptionDisp = firstDisconnectionValueObs
                .flatMap(new Function<BleException, ObservableSource<?>>() {
                    @Override
                    public ObservableSource<?> apply(BleException e) {
                        return Observable.error(e);
                    }
                })
                .subscribe(firstDisconnectionExceptionObs);
        disp.add(valueDisp);
        disp.add(exceptionDisp);
        final Pair<BehaviorRelay<BleException>, Disposable> old =
                connectionMap.replace(device.getAddress(), new Pair<BehaviorRelay<BleException>, Disposable>(relay, disp));
        if (old != null) {
            old.second.dispose();
        }
    }

    public void removeDevice(BluetoothDevice device) {
        final Pair<BehaviorRelay<BleException>, Disposable> pair = connectionMap.remove(device.getAddress());
        if (pair != null) {
            pair.second.dispose();
        }
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

    public void onDisconnectedException(BluetoothDevice device, BleDisconnectedException disconnectedException) {
        final Pair<BehaviorRelay<BleException>, Disposable> pair = connectionMap.remove(device.getAddress());
        if (pair != null) {
            pair.first.accept(disconnectedException);
            pair.second.dispose();
        }
    }

    public void onGattConnectionStateException(BluetoothDevice device, BleGattServerException disconnectedGattException) {
        final Pair<BehaviorRelay<BleException>, Disposable> pair = connectionMap.remove(device.getAddress());
        if (pair != null) {
            pair.first.accept(disconnectedGattException);
            pair.second.dispose();
        }
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

    public <T> Observable<T> asErrorOnlyObservable(BluetoothDevice device) {
        final Pair<BehaviorRelay<BleException>, Disposable> pair = connectionMap.get(device.getAddress());
        if (pair != null) {
            return pair.first
                    .firstElement()
                    .toObservable()
                    .flatMap(new Function<BleException, ObservableSource<T>>() {
                        @Override
                        public ObservableSource<T> apply(@NonNull BleException e) throws Exception {
                            return Observable.error(e);
                        }
                    });
        } else {
            return Observable.never();
        }
    }
}
