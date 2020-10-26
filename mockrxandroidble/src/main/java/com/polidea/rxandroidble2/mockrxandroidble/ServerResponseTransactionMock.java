package com.polidea.rxandroidble2.mockrxandroidble;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.ServerResponseTransaction;

import io.reactivex.Completable;

public class ServerResponseTransactionMock implements
        ServerResponseTransaction, Comparable<ServerResponseTransaction> {

    private final BluetoothDevice mDevice;
    private final int mOffset;
    private final int mRequestid;
    private final byte[] mValue;
    private final boolean returnval;

    public ServerResponseTransactionMock(
            int requestID,
            int offset,
            byte[] value,
            BluetoothDevice device,
            boolean returnval
    ) {
        mRequestid = requestID;
        mOffset = offset;
        mValue = value;
        mDevice = device;
        this.returnval = returnval;
    }

    @Override
    public int getRequestID() {
        return mRequestid;
    }

    @Override
    public int compareTo(ServerResponseTransaction o) {
        return o.getRequestID() - mRequestid;
    }

    @Override
    public Completable sendReply(int status, int offset, byte[] value) {
        return Completable.complete();
    }

    @Override
    public byte[] getValue() {
        return mValue;
    }

    @Override
    public BluetoothDevice getRemoteDevice() {
        return mDevice;
    }

    @Override
    public int getOffset() {
        return mOffset;
    }
}
