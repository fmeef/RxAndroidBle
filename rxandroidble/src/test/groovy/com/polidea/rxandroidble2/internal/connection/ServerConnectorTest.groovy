package com.polidea.rxandroidble2.internal.connection

import android.bluetooth.BluetoothManager
import io.reactivex.schedulers.TestScheduler
import spock.lang.Specification

public class ServerConnectorTest extends Specification {
    BluetoothManager bluetoothManager = Mock BluetoothManager
    def testScheduler = new TestScheduler()
    ServerConnector objectUnderTest

    def setup() {

    }
}