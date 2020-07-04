package com.polidea.rxandroidble2.exceptions;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;

import androidx.annotation.Nullable;

import com.polidea.rxandroidble2.utils.GattStatusParser;

public class BleGattServerException extends BleException {
    public static final int UNKNOWN_STATUS = -1;
    private final int status;
    private final BleGattServerOperationType bleGattOperationType;
    private final BluetoothDevice device;

    @Deprecated
    public BleGattServerException(int status, BluetoothDevice device, BleGattServerOperationType bleGattOperationType) {
        super(createMessage(null, status, bleGattOperationType));
        this.status = status;
        this.device = device;
        this.bleGattOperationType = bleGattOperationType;
    }

    public BleGattServerException(BluetoothGattServer gatt,
                                  BluetoothDevice device,
                                  BleGattServerOperationType bleGattOperationType) {
        this(UNKNOWN_STATUS, device, bleGattOperationType);
    }

    public BleGattServerOperationType getBleGattOperationType() {
        return bleGattOperationType;
    }

    public int getStatus() {
        return status;
    }

    @SuppressLint("DefaultLocale")
    private static String createMessage(@Nullable BluetoothGattServer gatt, int status, BleGattServerOperationType bleGattOperationType) {
        if (status == UNKNOWN_STATUS) {
            return "GATT Server exception (unknown)";
        }

        final String statusDescription = GattStatusParser.getGattCallbackStatusDescription(status);
        final String link
                = "https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/android-5.1.0_r1/stack/include/gatt_api.h";
        return String.format("GATT exception from %s, status %d (%s), type %s. (Look up status 0x%02x here %s)",
                            status, statusDescription, bleGattOperationType, status, link);
    }
}
