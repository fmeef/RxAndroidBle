package com.polidea.rxandroidble2.internal.server;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import androidx.annotation.Nullable;

import com.polidea.rxandroidble2.RxBleServer;
import com.polidea.rxandroidble2.ServerScope;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import bleshadow.javax.inject.Inject;

@ServerScope
public class RxBleServerStateImpl implements RxBleServerState {

    private final ConcurrentHashMap<UUID, NotificationStatus> notificationState = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BluetoothGattCharacteristic> characteristicMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BluetoothGattDescriptor> descriptorMap = new ConcurrentHashMap<>();
    private final BluetoothGattServerProvider provider;

    @Inject
    public RxBleServerStateImpl(
            BluetoothGattServerProvider provider
    ) {
        this.provider = provider;
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
    public void registerService(BluetoothGattService service) {
        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
            if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0
                    || (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0) {
                characteristic.addDescriptor(new BluetoothGattDescriptor(
                        RxBleServer.CLIENT_CONFIG,
                        BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ
                ));
            }
            characteristicMap.put(characteristic.getUuid(), characteristic);
            for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                descriptorMap.put(descriptor.getUuid(), descriptor);
            }
        }
        provider.getBluetoothGatt().addService(service);
    }

    @Override
    public BluetoothGattCharacteristic getCharacteristic(UUID uuid) {
        return characteristicMap.get(uuid);
    }

    @Override
    @Nullable
    public BluetoothGattService getService(UUID uuid) {
        return provider.getBluetoothGatt().getService(uuid);
    }

    @Override
    @Nullable
    public List<BluetoothGattService> getServiceList() {
        return provider.getBluetoothGatt().getServices();
    }

    @Nullable
    @Override
    public BluetoothGattDescriptor getDescriptor(UUID uuid) {
        return descriptorMap.get(uuid);
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
        } else {
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
