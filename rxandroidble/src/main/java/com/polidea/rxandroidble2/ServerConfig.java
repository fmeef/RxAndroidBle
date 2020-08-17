package com.polidea.rxandroidble2;

import android.bluetooth.BluetoothGattService;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public class ServerConfig {
    private final Map<UUID, BluetoothGattService> serviceList = new HashMap<>();
    private final Set<BluetoothPhy> phySet = new TreeSet<>();
    public enum BluetoothPhy {
        PHY_LE_1M,
        PHY_LE_2M,
        PHY_LE_CODED
    }

    public void addPhy(BluetoothPhy phy) {
        phySet.add(phy);
    }

    public void removePhy(BluetoothPhy phy) {
        phySet.remove(phy);
    }

    public void addService(BluetoothGattService service) {
        serviceList.put(service.getUuid(), service);
    }

    public void removeService(BluetoothGattService service) {
        serviceList.remove(service.getUuid());
    }

    public Map<UUID, BluetoothGattService> getServices() {
        return serviceList;
    }

    public Set<BluetoothPhy> getPhySet() {
        return phySet;
    }
}
