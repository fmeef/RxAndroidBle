package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothGattDescriptor;

import com.polidea.rxandroidble2.ServerConfig;
import com.polidea.rxandroidble2.ServerScope;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import bleshadow.javax.inject.Inject;

@ServerScope
public class RxBleServerStateImpl implements RxBleServerState {

    private final ServerConfig serverConfig;
    private final ConcurrentHashMap<UUID, NotificationStatus> notificationState = new ConcurrentHashMap<>();

    @Inject
    public RxBleServerStateImpl(
            ServerConfig serverConfig
    ) {
        this.serverConfig = serverConfig;
    }

    @Override
    public void enableNotifications(UUID uuid) {
        notificationState.put(uuid, NotificationStatus.NOTIFICATIONS_ENABLED);
    }

    @Override
    public void enableIndications(UUID uuid) {
        notificationState.put(uuid, NotificationStatus.INDICATIONS_ENABLED);
    }

    @Override
    public void disableNotifications(UUID uuid) {
        notificationState.put(uuid, NotificationStatus.NOTIFICATIONS_INDICATIONS_DISABLED);
    }

    @Override
    public byte[] getNotificationValue(UUID uuid) {
        final NotificationStatus status = notificationState.get(uuid);
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
    public void setNotifications(UUID characteristic, byte[] value) {
        if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
            enableNotifications(characteristic);
        } else if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
            enableIndications(characteristic);
        } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
            disableNotifications(characteristic);
        }
    }

    @Override
    public boolean getNotifications(UUID uuid) {
        NotificationStatus status = notificationState.get(uuid);
        return status == NotificationStatus.NOTIFICATIONS_ENABLED;
    }

    @Override
    public boolean getIndications(UUID uuid) {
        NotificationStatus status = notificationState.get(uuid);
        return status == NotificationStatus.INDICATIONS_ENABLED;
    }
}
