package com.polidea.rxandroidble2;

import io.reactivex.Observable;

public abstract class RxBleBase {
    @SuppressWarnings("WeakerAccess")
    public enum State {
        /**
         * Bluetooth Adapter is not available on the given OS. Most functions will throw {@link UnsupportedOperationException} when called.
         */
        BLUETOOTH_NOT_AVAILABLE,
        /**
         * Location permission is not given. Scanning and connecting to a device will not work. Used on API >=23.
         */
        LOCATION_PERMISSION_NOT_GRANTED,
        /**
         * Bluetooth Adapter is not switched on. Scanning and connecting to a device will not work.
         */
        BLUETOOTH_NOT_ENABLED,
        /**
         * Location Services are switched off. Scanning will not work. Used on API >=23.
         */
        LOCATION_SERVICES_NOT_ENABLED,
        /**
         * Everything is ready to be used.
         */
        READY
    }

    /**
     * Returns an observable emitting state _changes_ of the RxBleClient environment which may be helpful in deciding if particular
     * functionality should be used at a given moment.
     *
     * @see #getState() for {@link State} precedence order
     *
     * Examples:
     * - If the device is in {@link State#READY} and the user will turn off the bluetooth adapter then {@link State#BLUETOOTH_NOT_ENABLED}
     * will be emitted.
     * - If the device is in {@link State#BLUETOOTH_NOT_ENABLED} then changing state of Location Services will not cause emissions
     * because of the checks order
     * - If the device is in {@link State#BLUETOOTH_NOT_AVAILABLE} then this {@link Observable} will complete because any other checks
     * will not be performed as devices are not expected to obtain bluetooth capabilities during runtime
     *
     * To get the initial {@link State} and then observe changes you can use: `observeStateChanges().startWith(getState())`.
     *
     * @return the observable
     */
    public abstract Observable<State> observeStateChanges();

    /**
     * Returns the current state of the RxBleClient environment, which may be helpful in deciding if particular functionality
     * should be used at a given moment. The function concentrates on states that are blocking the full functionality of the library.
     *
     * Checking order:
     * 1. Is Bluetooth available?
     * 2. Is Location Permission granted? (if needed = API>=23)
     * 3. Is Bluetooth Adapter on?
     * 4. Are Location Services enabled? (if needed = API>=23)
     *
     * If any of the checks fails an appropriate State is returned and next checks are not performed.
     *
     * State precedence order is as follows:
     * {@link State#BLUETOOTH_NOT_AVAILABLE} if check #1 fails,
     * {@link State#LOCATION_PERMISSION_NOT_GRANTED} if check #2 fails,
     * {@link State#BLUETOOTH_NOT_ENABLED} if check #3 fails,
     * {@link State#LOCATION_SERVICES_NOT_ENABLED} if check #4 fails,
     * {@link State#READY}
     *
     * @return the current state
     */
    public abstract State getState();
}
