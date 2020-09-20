package com.polidea.rxandroidble2.internal.server;

import java.util.UUID;

public interface RxBleServerState {
    void enableNotifications(UUID uuid);
    void enableIndications(UUID uuid);
    void disableNotifications(UUID uuid);
    byte[] getNotificationValue(UUID uuid);
    void setNotifications(UUID characteristic, byte[] value);
}
