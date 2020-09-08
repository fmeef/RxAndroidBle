package com.polidea.rxandroidble2.internal.server;

import android.util.Log;

import com.polidea.rxandroidble2.ServerConnectionScope;
import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.internal.connection.ConnectionSubscriptionWatcher;
import com.polidea.rxandroidble2.internal.connection.DisconnectionRouterOutput;

import bleshadow.javax.inject.Inject;
import io.reactivex.functions.Consumer;
import io.reactivex.observers.DisposableObserver;

@ServerConnectionScope
public class ServerDisconnectAction implements ConnectionSubscriptionWatcher {
    private final DisconnectionRouterOutput disconnectionRouterOutput;
    private DisposableObserver<BleException> disconnectionThrowableSubscription;

    @Inject
    ServerDisconnectAction(final DisconnectionRouterOutput disconnectionRouterOutput) {
        this.disconnectionRouterOutput = disconnectionRouterOutput;
    }

    @Override
    public void onConnectionSubscribed() {
        disconnectionThrowableSubscription = disconnectionRouterOutput.asValueOnlyObservable()
                .subscribeWith(new DisposableObserver<BleException>() {
                    @Override
                    public void onNext(BleException e) {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });

        disconnectionRouterOutput.asErrorOnlyObservable()
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.e("debug", "onDisconnect");
                    }
                });
    }

    @Override
    public void onConnectionUnsubscribed() {
       disconnectionThrowableSubscription.dispose();
       disconnectionThrowableSubscription = null;
    }
}
