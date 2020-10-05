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
    private final Timeout operationTimeout;

    public static ServerConfig newInstance(Timeout operationTimeout) {
        return new ServerConfig(operationTimeout);
    }

    private ServerConfig(Timeout operationTimeout) {
        this.operationTimeout = operationTimeout;
    }

    public enum BluetoothPhy {
        PHY_LE_1M,
        PHY_LE_2M,
        PHY_LE_CODED
    }

    public ServerConfig addPhy(BluetoothPhy phy) {
        phySet.add(phy);
        return this;
    }

    public ServerConfig removePhy(BluetoothPhy phy) {
        phySet.remove(phy);
        return this;
    }

    public ServerConfig addService(BluetoothGattService service) {
        serviceList.put(service.getUuid(), service);
        return this;
    }

    public ServerConfig removeService(BluetoothGattService service) {
        serviceList.remove(service.getUuid());
        return this;
    }

    public Timeout getOperationTimeout() {
        return operationTimeout;
    }

    public Map<UUID, BluetoothGattService> getServices() {
        return serviceList;
    }

    public Set<BluetoothPhy> getPhySet() {
        return phySet;
    }

}
