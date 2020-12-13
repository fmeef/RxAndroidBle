package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;

import com.polidea.rxandroidble2.ServerComponent;
import com.polidea.rxandroidble2.ServerConnectionComponent;
import com.polidea.rxandroidble2.ServerConnectionScope;
import com.polidea.rxandroidble2.internal.operations.TimeoutConfiguration;
import com.polidea.rxandroidble2.internal.server.BluetoothGattServerProvider;
import com.polidea.rxandroidble2.internal.server.RxBleGattServerCallback;

import bleshadow.javax.inject.Inject;
import bleshadow.javax.inject.Named;
import io.reactivex.Scheduler;

@ServerConnectionScope
public class ServerConnectionOperationsProviderImpl implements ServerConnectionOperationsProvider {

    private final Scheduler gattServerScheduler;
    private final BluetoothDevice bluetoothDevice;
    private final RxBleGattServerCallback callback;
    private final BluetoothManager bluetoothManager;
    private final TimeoutConfiguration timeoutConfiguration;
    private final BluetoothGattServerProvider serverProvider;


    @Inject
    public ServerConnectionOperationsProviderImpl(
            @Named(ServerComponent.NamedSchedulers.BLUETOOTH_CONNECTION) Scheduler gattServerScheduler,
            BluetoothDevice bluetoothDevice,
            RxBleGattServerCallback callback,
            BluetoothManager bluetoothManager,
            @Named(ServerConnectionComponent.OPERATION_TIMEOUT) TimeoutConfiguration timeoutConfiguration,
            BluetoothGattServerProvider serverProvider
    ) {
        this.gattServerScheduler = gattServerScheduler;
        this.bluetoothDevice = bluetoothDevice;
        this.callback = callback;
        this.bluetoothManager = bluetoothManager;
        this.timeoutConfiguration = timeoutConfiguration;
        this.serverProvider = serverProvider;
    }




    public ServerReplyOperation provideReplyOperation(
            BluetoothDevice device,
            int requestID,
            int status,
            int offset,
            byte[] value
    ) {
        return new ServerReplyOperation(
                timeoutConfiguration,
                serverProvider.getBluetoothGatt(),
                device,
                requestID,
                status,
                offset,
                value
        );
    }

    public ServerDisconnectOperation provideDisconnectOperation(BluetoothDevice device) {
        return new ServerDisconnectOperation(
                serverProvider,
                device,
                callback,
                gattServerScheduler,
                bluetoothManager,
                timeoutConfiguration
                );
    }

    @Override
    public NotifyCharacteristicChangedOperation provideNotifyOperation(
            BluetoothGattCharacteristic characteristic,
            byte[] value,
            boolean isIndication
    ) {
        return new NotifyCharacteristicChangedOperation(
                serverProvider,
                characteristic,
                timeoutConfiguration,
                serverProvider.getConnection(bluetoothDevice),
                value,
                isIndication
        );
    }
}
