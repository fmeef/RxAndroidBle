package com.polidea.rxandroidble2.internal.util;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import androidx.annotation.NonNull;

import com.polidea.rxandroidble2.ServerResponseTransaction;

import java.util.UUID;

import io.reactivex.Observable;

public class GattServerTransaction<T> implements ServerResponseTransaction {

    public final T first;
    private final ServerResponseTransaction second;

    public GattServerTransaction(@NonNull T first, ServerResponseTransaction second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public int getRequestID() {
        return second.getRequestID();
    }

    @Override
    public int compareTo(ServerResponseTransaction o) {
        return second.compareTo(o);
    }

    @Override
    public Observable<Boolean> sendReply(int status, int offset, byte[] value) {
        return second.sendReply(status, offset, value);
    }

    @Override
    public byte[] getValue() {
        return second.getValue();
    }

    @Override
    public BluetoothDevice getRemoteDevice() {
        return second.getRemoteDevice();
    }

    @Override
    public int getOffset() {
        return second.getOffset();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GattServerTransaction)) {
            return false;
        }
        GattServerTransaction<?> ba = (GattServerTransaction<?>) o;
        return ba.second.compareTo(second) == 0 && ba.first.equals(first);
    }

    @Override
    public int hashCode() {
        return first.hashCode() ^ second.hashCode();
    }

    @Override
    public String toString() {
        String firstDescription;
        if (first instanceof BluetoothGattCharacteristic) {
            firstDescription = BluetoothGattCharacteristic.class.getSimpleName()
                    + "(" + ((BluetoothGattCharacteristic) first).getUuid().toString() + ")";
        } else if (first instanceof BluetoothGattDescriptor) {
            firstDescription = BluetoothGattDescriptor.class.getSimpleName()
                    + "(" + ((BluetoothGattDescriptor) first).getUuid().toString() + ")";
        } else if (first instanceof UUID) {
            firstDescription = UUID.class.getSimpleName()
                    + "(" + first.toString() + ")";
        } else {
            firstDescription = first.getClass().getSimpleName();
        }
        return getClass().getSimpleName() + "[first=" + firstDescription + ", second=" + second + "]";
    }

    public static <T> GattServerTransaction<T> create(T first, ServerResponseTransaction bytes) {
        return new GattServerTransaction<>(first, bytes);
    }

    public T getPayload() {
        return first;
    }
}
