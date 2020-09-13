package com.polidea.rxandroidble2.internal.operations.server

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattServer
import com.polidea.rxandroidble2.internal.serialization.QueueReleaseInterface
import com.polidea.rxandroidble2.internal.util.MockOperationTimeoutConfiguration
import io.reactivex.schedulers.TestScheduler
import spock.lang.Specification

import java.util.concurrent.TimeUnit

public class ServerReplyOperationTest extends Specification {
    static long timeout = 10
    static TimeUnit timeoutTimeUnit = TimeUnit.SECONDS
    private static long DEFAULT_WRITE_DELAY = 1
    BluetoothGattServer mockGattServer = Mock BluetoothGattServer
    TestScheduler testScheduler = new TestScheduler()
    BluetoothDevice device
    QueueReleaseInterface mockQueueReleaseInterface = Mock QueueReleaseInterface
    ServerReplyOperation objectUnderTest
    byte[] value = [1, 2, 3, 4, 5, 6, 7, 8, 9]
    int requestID = 1
    int status = 0
    int offset = 0

    def setup() {
        device = Mock BluetoothDevice
        objectUnderTest = new ServerReplyOperation(
                testScheduler,
                new MockOperationTimeoutConfiguration(timeout.intValue(), testScheduler),
                mockGattServer,
                device,
                requestID,
                status,
                offset,
                value
        )
    }

    def "runs correctly when sendResponse returns true"() {

        when:
        def testSubscriber = objectUnderTest.run(mockQueueReleaseInterface).test()
        advanceTimeForWritesToComplete(1)

        then:
        1 * mockGattServer.sendResponse(device, requestID, status, offset, value) >> true

        then:
        testSubscriber.assertNoErrors()

        then:
        testSubscriber.assertValue(true)

    }

    def "fails when sendResponse returns false"() {
        when:
        def testSubscriber = objectUnderTest.run(mockQueueReleaseInterface).test()
        advanceTimeForWritesToComplete(1)

        then:
        1 * mockGattServer.sendResponse(device, requestID, status, offset, value) >> false

        then:
        testSubscriber.assertValue(false)
    }

    private advanceTimeForWrites(long numberOfWrites) {
        testScheduler.advanceTimeBy(numberOfWrites * DEFAULT_WRITE_DELAY, TimeUnit.SECONDS)
    }

    private advanceTimeForWritesToComplete(long numberOfWrites) {
        advanceTimeForWrites(numberOfWrites + 1)
    }

}