package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothManager;

import com.polidea.rxandroidble2.ClientComponent;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.ServerConnectionComponent;
import com.polidea.rxandroidble2.ServerConnectionScope;
import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.server.RxBleServerConnectionInternal;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import bleshadow.javax.inject.Provider;
import io.reactivex.Scheduler;

@ServerConnectionScope
public class ServerConnectionOperationsProviderImpl implements ServerConnectionOperationsProvider {

    private final Scheduler gattServerScheduler;
    private final BluetoothManager bluetoothManager;
    private final TimeoutConfiguration timeoutConfiguration;
    private final Provider<BluetoothGattServer> server;
    private final Provider<RxBleServerConnectionInternal> connection;


    @Inject
    public ServerConnectionOperationsProviderImpl(
            @Named(ClientComponent.NamedSchedulers.BLUETOOTH_INTERACTION) Scheduler gattServerScheduler,
            BluetoothManager bluetoothManager,
            @Named(ServerConnectionComponent.OPERATION_TIMEOUT) TimeoutConfiguration timeoutConfiguration,
            Provider<BluetoothGattServer> server,
            Provider<RxBleServerConnectionInternal> connection
    ) {
        this.gattServerScheduler = gattServerScheduler;
        this.bluetoothManager = bluetoothManager;
        this.timeoutConfiguration = timeoutConfiguration;
        this.server = server;
        this.connection = connection;
    }

    public ServerReplyOperation provideReplyOperation(
            RxBleDevice device,
            int requestID,
            int status,
            int offset,
            byte[] value
    ) {
        return new ServerReplyOperation(
                server.get(),
                device,
                requestID,
                status,
                offset,
                value
        );
    }

    public ServerDisconnectOperation provideDisconnectOperation(RxBleDevice device) {
        return new ServerDisconnectOperation(
                server.get(),
                device,
                connection.get(),
                gattServerScheduler,
                bluetoothManager,
                timeoutConfiguration
                );
    }

    @Override
    public NotifyCharacteristicChangedOperation provideNotifyOperation(
            BluetoothGattCharacteristic characteristic,
            byte[] value,
            boolean isIndication,
            RxBleDevice device
    ) {
        return new NotifyCharacteristicChangedOperation(
                server.get(),
                characteristic,
                timeoutConfiguration,
                connection.get(),
                value,
                isIndication,
                device
        );
    }
}
