package com.polidea.rxandroidble2.internal.util;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import androidx.annotation.NonNull;

import com.polidea.rxandroidble2.ServerResponseTransaction;

import java.util.UUID;

public class TransactionAssociation<T> {

    public final T first;
    public final ServerResponseTransaction second;

    public TransactionAssociation(@NonNull T first, ServerResponseTransaction second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TransactionAssociation)) {
            return false;
        }
        TransactionAssociation<?> ba = (TransactionAssociation<?>) o;
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

    public static <T> TransactionAssociation<T> create(T first, ServerResponseTransaction bytes) {
        return new TransactionAssociation<>(first, bytes);
    }
}