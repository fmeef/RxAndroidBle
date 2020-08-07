package com.polidea.rxandroidble2.internal.server;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MultiIndexImpl<T, V, U> implements MultiIndex<T, V, U> {
    private final Map<T, U> deviceMap = new HashMap<>();
    private final Map<U, V> reverseRequestIdMap = new HashMap<>();
    private final Map<V, U> requestIdMap = new HashMap<>();

    public MultiIndexImpl() {
    }

    @Override
    public U getMulti(V requestid) {
        return requestIdMap.get(requestid);
    }

    @Override
    public U putMulti(@NonNull V integer, @NonNull U payload) {
        if (!deviceMap.containsValue(payload)) {
            return null;
        }

        requestIdMap.put(integer, payload);
        reverseRequestIdMap.put(payload, integer);
        return payload;
    }

    @Override
    public int size() {
        return deviceMap.size();
    }

    @Override
    public boolean isEmpty() {
        return deviceMap.isEmpty();
    }

    @Override
    public boolean containsKey(@Nullable Object o) {
        return deviceMap.containsKey(o);
    }

    @Override
    public boolean containsValue(@Nullable Object o) {
        return deviceMap.containsValue(o);
    }

    @Nullable
    @Override
    public U get(@Nullable Object o) {
        return deviceMap.get(o);
    }

    @Nullable
    @Override
    public U put(@NonNull T device, @NonNull U rxBleServerConnection) {
        return deviceMap.put(device, rxBleServerConnection);
    }

    @Nullable
    @Override
    public U remove(@Nullable Object o) {
        U connection = deviceMap.remove(o);
        if (reverseRequestIdMap.containsKey(connection)) {
            V key = reverseRequestIdMap.get(connection);
            if (key != null) {
                requestIdMap.remove(key);
            }
        }
        return connection;
    }

    @Override
    public void putAll(@NonNull Map<? extends T, ? extends U> map) {
        deviceMap.putAll(map);
    }

    @Override
    public void clear() {
        deviceMap.clear();
        reverseRequestIdMap.clear();
        requestIdMap.clear();
    }

    @NonNull
    @Override
    public Set<T> keySet() {
        return deviceMap.keySet();
    }

    @NonNull
    @Override
    public Collection<U> values() {
        return deviceMap.values();
    }

    @NonNull
    @Override
    public Set<Entry<T, U>> entrySet() {
        return deviceMap.entrySet();
    }
}
