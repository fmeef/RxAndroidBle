package com.polidea.rxandroidble2.exceptions;

public class BleGattServerOperationType {
    public static final BleGattServerOperationType CONNECTION_STATE =
            new BleGattServerOperationType("CONNECTION_STATE");
    public static final BleGattServerOperationType SERVICE_ADDED =
            new BleGattServerOperationType("SERVICE_ADDED");
    public static final BleGattServerOperationType CHARACTERISTIC_READ_REQUEST =
            new BleGattServerOperationType("CHARACTERISTIC_READ_REQUEST");
    public static final BleGattServerOperationType CHARACTERISTIC_WRITE_REQUEST =
            new BleGattServerOperationType("CHARACTERISTIC_WRITE_REQUEST");
    public static final BleGattServerOperationType CHARACTERISTIC_LONG_WRITE_REQUEST =
            new BleGattServerOperationType("CHARACTERISTIC_LONG_WRITE_REQUEST");
    public static final BleGattServerOperationType DESCRIPTOR_LONG_WRITE_REQUEST =
            new BleGattServerOperationType("DESCRIPTOR_LONG_WRITE_REQUEST");
    public static final BleGattServerOperationType DESCRIPTOR_READ_REQUEST =
            new BleGattServerOperationType("DESCRIPTOR_READ_REQUEST");
    public static final BleGattServerOperationType DESCRIPTOR_WRITE_REQUEST =
            new BleGattServerOperationType("DESCRIPTOR_WRITE_REQUEST");
    public static final BleGattServerOperationType NOTIFICATION_SENT =
            new BleGattServerOperationType("NOTIFICATION_SENT");
    public static final BleGattServerOperationType ON_MTU_CHANGED =
            new BleGattServerOperationType("ON_MTU_CHANGED");

    private final String description;

    private BleGattServerOperationType(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "BleGattServerOperation{" + "description='" + description + '\'' + '}';
    }
}
