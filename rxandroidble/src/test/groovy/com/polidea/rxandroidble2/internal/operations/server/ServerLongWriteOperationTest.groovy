package com.polidea.rxandroidble2.internal.operations.server;

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattServer
import com.polidea.rxandroidble2.internal.operations.server.ServerLongWriteOperation
import com.polidea.rxandroidble2.internal.serialization.QueueReleaseInterface
import io.reactivex.Observable
import io.reactivex.annotations.NonNull
import io.reactivex.functions.Predicate
import io.reactivex.schedulers.TestScheduler
import spock.lang.Specification

import java.util.concurrent.TimeUnit

public class ServerLongWriteOperationTest extends Specification {
    private static long DEFAULT_WRITE_DELAY = 1
    BluetoothGattServer mockGattServer = Mock BluetoothGattServer
    TestScheduler testScheduler = new TestScheduler()
    BluetoothDevice device = Mock BluetoothDevice
    QueueReleaseInterface mockQueueReleasingInterface = Mock QueueReleaseInterface
    byte[] data = [1,2,3,4,5,6,7,8,9,0]
    long repeat = 10
    byte[] finaldata = new byte[data.length * repeat]
    Observable<byte[]> inputBytesObservable
    ServerLongWriteOperation objectUnderTest

    def setup() {
        inputBytesObservable = Observable.just(data).repeat(repeat)
        for (int i=0;i<finaldata.length;i++) {
            finaldata[i] = data[i % data.length]
        }
        objectUnderTest = new ServerLongWriteOperation(
                testScheduler,
                inputBytesObservable,
                device
        )
    }

    private def "output bytes are the same as standard array contatenation"() {
        when:
        def testSubscriber = objectUnderTest.run(mockQueueReleasingInterface).test()
        advanceTimeForWritesToComplete(repeat)

        then:
        testSubscriber.assertValue(new Predicate<byte[]>() {
            @Override
            boolean test(@NonNull byte[] bytes) throws Exception {
                return Arrays.equals(bytes, finaldata);
            }
        })
    }

    private advanceTimeForWrites(long numberOfWrites) {
        testScheduler.advanceTimeBy(numberOfWrites * DEFAULT_WRITE_DELAY, TimeUnit.SECONDS)
    }

    private advanceTimeForWritesToComplete(long numberOfWrites) {
        advanceTimeForWrites(numberOfWrites + 1)
    }

}