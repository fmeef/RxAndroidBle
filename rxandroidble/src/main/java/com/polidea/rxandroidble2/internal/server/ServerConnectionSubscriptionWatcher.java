package com.polidea.rxandroidble2.internal.server;

import com.polidea.rxandroidble2.internal.connection.ConnectionModule;
import com.polidea.rxandroidble2.internal.connection.ConnectorImpl;

/**
 * Interface for all classes that should be called when the user subscribes to/disposes
 * {@link com.polidea.rxandroidble2.RxBleDevice#establishConnection(boolean)}
 *
 * The binding which injects the interface to a {@link ConnectorImpl} is in {@link ConnectionModule}
 */
public interface ServerConnectionSubscriptionWatcher {

    /**
     * Method to be called when the user subscribes to an individual
     * {@link com.polidea.rxandroidble2.RxBleDevice#establishConnection(boolean)}
     */
    void onConnectionSubscribed();

    /**
     * Method to be called when the user disposes an individual
     * {@link com.polidea.rxandroidble2.RxBleDevice#establishConnection(boolean)}
     */
    void onConnectionUnsubscribed();
}

