package com.polidea.rxandroidble2.internal.server

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import com.jakewharton.rxrelay2.PublishRelay
import com.polidea.rxandroidble2.DummyOperationQueue
import com.polidea.rxandroidble2.internal.operations.server.ServerOperationsProvider
import com.polidea.rxandroidble2.internal.operations.server.ServerOperationsProviderImpl
import com.polidea.rxandroidble2.internal.serialization.QueueReleaseInterface
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.annotations.NonNull
import io.reactivex.functions.Predicate
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import spock.lang.Specification

import java.util.concurrent.TimeUnit

public class RxBleServerConnectionTest extends Specification {
    public static long DEFAULT_WRITE_DELAY = 1
    UUID testUuid = UUID.randomUUID()
    def testScheduler = new TestScheduler()
    ServerOperationsProvider operationsProvider
    DummyOperationQueue dummyQueue = new DummyOperationQueue()
    ServerDisconnectionRouter disconnectionRouter = Mock ServerDisconnectionRouter
    BluetoothDevice bluetoothDevice = Mock BluetoothDevice
    RxBleServerConnection objectUnderTest
    RxBleGattServerCallback callback = Mock RxBleGattServerCallback
    QueueReleaseInterface mockQueueReleasingInterface = Mock QueueReleaseInterface
    BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
            testUuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
    )
    int requestID  = 1
    byte[] data = [1,2,3,4,5,6,7,8,9,0]
    long repeat = 10
    byte[] finaldata = new byte[data.length * repeat]
    Observable<byte[]> inputBytesObservable


    def setup() {
        inputBytesObservable = Observable.just(data).repeat(repeat)
        for (int i=0;i<finaldata.length;i++) {
            finaldata[i] = data[i % data.length]
        }
        operationsProvider = new ServerOperationsProviderImpl(
                callback,
                testScheduler,
                Mock(BluetoothGattServer)
        )
        objectUnderTest = new RxBleServerConnectionImpl(
                testScheduler,
                operationsProvider,
                dummyQueue,
                bluetoothDevice,
                disconnectionRouter
        )
    }


    def "longWriteOutput works"() {
        when:
        RxBleServerConnection.Output<byte[]> output = objectUnderTest.openLongWriteOutput(requestID, characteristic)
        PublishSubject<byte[]> o = output.valueRelay
        o.onNext(finaldata)
        advanceTimeForWritesToComplete(1)

        then:
        def testsubscriber = objectUnderTest.getLongWriteObservable(requestID).test()
        testsubscriber.assertValue(new Predicate<byte[]>() {
            @Override
            boolean test(@NonNull byte[] bytes) throws Exception {
                return Arrays.equals(bytes, finaldata)
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