package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import com.polidea.rxandroidble2.RxBleClient;

import java.util.UUID;

public interface RxBleServerState {
    BluetoothGattDescriptor getDescriptor(UUID characteristic, UUID uuid);
    byte[] getNotificationValue(UUID uuid);
    RxBleClient.NotificationStatus getNotificationStatus(UUID characteristic);
    void setNotifications(UUID characteristic, byte[] value);
    boolean getIndications(UUID uuid);
    boolean getNotifications(UUID uuid);
    void enableNotifications(UUID uuid);
    void enableIndications(UUID uuid);
    void disableNotifications(UUID uuid);
    BluetoothGattCharacteristic getCharacteristic(UUID uuid);
    void addCharacteristic(UUID uuid, BluetoothGattCharacteristic characteristic);
}
