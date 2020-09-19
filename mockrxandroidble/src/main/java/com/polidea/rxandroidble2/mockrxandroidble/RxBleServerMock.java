package com.polidea.rxandroidble2.mockrxandroidble;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.RxBleServer;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternal;

import io.reactivex.Observable;

public class RxBleServerMock extends RxBleServer {
    //TODO:
    @Override
    public Observable<State> observeStateChanges() {
        return null;
    }

    @Override
    public State getState() {
        return null;
    }

    @Override
    public Observable<RxBleServerConnectionInternal> openServer() {
        return null;
    }

    @Override
    public void closeServer() {

    }

    @Override
    public RxBleServerConnectionInternal getConnection(BluetoothDevice device) {
        return null;
    }
}
