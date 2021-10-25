package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import androidx.annotation.Nullable;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.ServerConnectionScope;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import bleshadow.javax.inject.Inject;

@ServerConnectionScope
public class RxBleServerStateImpl implements RxBleServerState {


    private final ConcurrentHashMap<UUID, RxBleClient.NotificationStatus> notificationState = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BluetoothGattCharacteristic> characteristicMap = new ConcurrentHashMap<>();

    @Inject
    public RxBleServerStateImpl() {

    }

    @Nullable
    @Override
    public BluetoothGattDescriptor getDescriptor(UUID characteristic, UUID uuid) {
        BluetoothGattCharacteristic ch = characteristicMap.get(characteristic);
        if (ch == null) {
            return null;
        }
        return ch.getDescriptor(uuid);
    }

    @Override
    public byte[] getNotificationValue(UUID uuid) {
        final RxBleClient.NotificationStatus status = notificationState.get(uuid);
        if (status != null) {
            switch (status) {
                case INDICATIONS_ENABLED:
                    return BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                case NOTIFICATIONS_ENABLED:
                    return BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                default:
                    return BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
            }
        } else {
            return BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
        }
    }

    @Override
    public RxBleClient.NotificationStatus getNotificationStatus(UUID characteristic) {
        return notificationState.get(characteristic);
    }

    @Override
    public void setNotifications(UUID characteristic, byte[] value) {
        if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
            enableNotifications(characteristic);
        } else if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
            enableIndications(characteristic);
        } else {
            disableNotifications(characteristic);
        }
    }

    @Override
    public boolean getNotifications(UUID uuid) {
        RxBleClient.NotificationStatus status = notificationState.get(uuid);
        return status == RxBleClient.NotificationStatus.NOTIFICATIONS_ENABLED;
    }

    @Override
    public boolean getIndications(UUID uuid) {
        RxBleClient.NotificationStatus status = notificationState.get(uuid);
        return status == RxBleClient.NotificationStatus.INDICATIONS_ENABLED;
    }

    @Override
    public void enableNotifications(UUID uuid) {
        notificationState.put(uuid, RxBleClient.NotificationStatus.NOTIFICATIONS_ENABLED);
    }

    @Override
    public void enableIndications(UUID uuid) {
        notificationState.put(uuid, RxBleClient.NotificationStatus.INDICATIONS_ENABLED);
    }

    @Override
    public void disableNotifications(UUID uuid) {
        notificationState.put(uuid, RxBleClient.NotificationStatus.NOTIFICATIONS_INDICATIONS_DISABLED);
    }

    @Override
    public BluetoothGattCharacteristic getCharacteristic(UUID uuid) {
        return characteristicMap.get(uuid);
    }

    @Override
    public void addCharacteristic(UUID uuid, BluetoothGattCharacteristic characteristic) {
        characteristicMap.put(uuid, characteristic);
    }
}
