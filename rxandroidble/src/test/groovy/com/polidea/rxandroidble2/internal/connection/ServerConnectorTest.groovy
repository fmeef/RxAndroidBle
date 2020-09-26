package com.polidea.rxandroidble2.internal.connection

import android.bluetooth.BluetoothManager
import com.polidea.rxandroidble2.internal.server.BluetoothGattServerProvider
import io.reactivex.schedulers.TestScheduler
import spock.lang.Specification

public class ServerConnectorTest extends Specification {
    BluetoothGattServerProvider serverProvider = Mock BluetoothGattServerProvider
    BluetoothManager bluetoothManager = Mock BluetoothManager
    def testScheduler = new TestScheduler()
    ServerConnector objectUnderTest

    def setup() {

    }
}