package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.UUID;

public interface RxBleServerState {
    void enableNotifications(UUID uuid);
    void enableIndications(UUID uuid);
    void disableNotifications(UUID uuid);
    boolean getNotifications(UUID uuid);
    boolean getIndications(UUID uuid);
    byte[] getNotificationValue(UUID uuid);
    void setNotifications(UUID characteristic, byte[] value);
    void registerService(BluetoothGattService service);
    NotificationStatus getNotificationStatus(UUID characteristic);
    @Nullable
    BluetoothGattService getService(UUID uuid);
    @Nullable
    BluetoothGattCharacteristic getCharacteristic(UUID uuid);
    @Nullable
    BluetoothGattDescriptor getDescriptor(UUID characteristic, UUID uuid);
    List<BluetoothGattService> getServiceList();


    enum NotificationStatus {
        NOTIFICATIONS_INDICATIONS_DISABLED,
        NOTIFICATIONS_ENABLED,
        INDICATIONS_ENABLED
    }
}
