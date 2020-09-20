package com.polidea.rxandroidble2.internal.server;

import java.util.UUID;

public interface RxBleServerState {
    void enableNotifications(UUID uuid);
    void enableIndications(UUID uuid);
    void disableNotifications(UUID uuid);
    boolean getNotifications(UUID uuid);
    boolean getIndications(UUID uuid);
    byte[] getNotificationValue(UUID uuid);
    void setNotifications(UUID characteristic, byte[] value);

    enum NotificationStatus {
        NOTIFICATIONS_INDICATIONS_DISABLED,
        NOTIFICATIONS_ENABLED,
        INDICATIONS_ENABLED
    }
}
