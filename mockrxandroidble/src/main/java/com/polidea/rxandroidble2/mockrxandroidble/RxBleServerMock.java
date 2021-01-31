package com.polidea.rxandroidble2.mockrxandroidble;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.RxBleServer;
import com.polidea.rxandroidble2.RxBleServerConnection;
import com.polidea.rxandroidble2.ServerConfig;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;

public class RxBleServerMock extends RxBleServer {

    private final BehaviorSubject<BluetoothDevice> connectedDevices = BehaviorSubject.create();
    private final Map<BluetoothDevice, RxBleServerConnectionMock> connectionMap = new HashMap<>();


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
    public Observable<RxBleServerConnection> openServer(ServerConfig config) {
        return null;
    }

    @Override
    public RxBleServerConnection getConnection(BluetoothDevice device) {
        return null;
    }
}
