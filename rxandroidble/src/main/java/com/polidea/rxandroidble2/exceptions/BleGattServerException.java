package com.polidea.rxandroidble2.exceptions;

import android.annotation.SuppressLint;

import com.polidea.rxandroidble2.utils.GattStatusParser;

public class BleGattServerException extends BleException {
    public static final int UNKNOWN_STATUS = -1;
    private final int status;
    private final BleGattServerOperationType bleGattOperationType;

    public BleGattServerException(
            int status,
            BleGattServerOperationType bleGattOperationType,
            String reason
    ) {
        super(createMessage(reason, status, bleGattOperationType));
        this.status = status;
        this.bleGattOperationType = bleGattOperationType;
    }

    public BleGattServerException(
            BleGattServerOperationType bleGattOperationType,
            String reason
    ) {
        this(UNKNOWN_STATUS, bleGattOperationType, reason);
    }

    public BleGattServerOperationType getBleGattOperationType() {
        return bleGattOperationType;
    }

    public int getStatus() {
        return status;
    }

    @SuppressLint("DefaultLocale")
    private static String createMessage(String reason, int status, BleGattServerOperationType bleGattOperationType) {
        final String statusDescription = GattStatusParser.getGattCallbackStatusDescription(status);
        final String link
                = "https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/android-5.1.0_r1/stack/include/gatt_api.h";
        final String r;
        if (reason == null) {
            r = "unknown";
        } else {
            r = reason;
        }
        return String.format("GATT server exception: %s, status %d (%s), type %s. (Look up status 0x%02x here %s)",
                           r, status, statusDescription, bleGattOperationType, status, link);
    }
}
