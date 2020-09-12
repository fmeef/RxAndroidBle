package com.polidea.rxandroidble2.internal.server

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.polidea.rxandroidble2.DummyOperationQueue
import com.polidea.rxandroidble2.ServerTransactionFactory
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProvider
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProviderImpl
import com.polidea.rxandroidble2.internal.serialization.ServerConnectionOperationQueue
import io.reactivex.annotations.NonNull
import io.reactivex.functions.Predicate
import io.reactivex.schedulers.TestScheduler
import spock.lang.Specification

import java.util.concurrent.TimeUnit

public class RxBleServerConnectionTest extends Specification {
    public static long DEFAULT_WRITE_DELAY = 1
    UUID testUuid = UUID.randomUUID()
    TestScheduler testScheduler = new TestScheduler()
    ServerConnectionOperationsProvider operationsProvider
    ServerConnectionOperationQueue dummyQueue = new DummyOperationQueue()
    ServerDisconnectionRouter disconnectionRouter = Mock ServerDisconnectionRouter
    BluetoothDevice bluetoothDevice = Mock BluetoothDevice
    BluetoothGattServerProvider bluetoothGattServer = Mock BluetoothGattServerProvider
    RxBleServerConnection objectUnderTest
    RxBleGattServerCallback callback = Mock RxBleGattServerCallback
    ServerTransactionFactory serverTransactionFactory = Mock ServerTransactionFactory
    BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
            testUuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
    )

    BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
            testUuid,
            BluetoothGattDescriptor.PERMISSION_READ
    )

    int requestID  = 1
    byte[] data = [1,2,3,4,5,6,7,8,9,0]
    long repeat = 10
    byte[] finaldata = new byte[data.length * repeat]


    def setup() {
        for (int i=0;i<finaldata.length;i++) {
            finaldata[i] = data[i % data.length]
        }

        operationsProvider = new ServerConnectionOperationsProviderImpl(
                testScheduler,
                bluetoothGattServer,
                bluetoothDevice,
                callback
        )

        objectUnderTest = new RxBleServerConnectionImpl(
                testScheduler,
                operationsProvider,
                dummyQueue,
                bluetoothDevice,
                disconnectionRouter,
                serverTransactionFactory
        )
    }


    def "longWriteOutput emits correct value"() {
        when:
        prepCharacteristicLongWriteOutput()
        def closeresult = objectUnderTest.closeLongWriteCharacteristicOutput(requestID).test()
        advanceTimeForWrites(1)

        then:
        closeresult.assertNoErrors()
        closeresult
                .assertNoErrors()
                .assertValue(new Predicate<byte[]>() {
            @Override
            boolean test(@NonNull byte[] bytes) throws Exception {
                return Arrays.equals(bytes, finaldata)
            }
        })

    }

    def "longWriteOutput handles descriptor write successfully"() {
        when:
        prepDescriptorLongWriteOutput()
        def closeresult = objectUnderTest.closeLongWriteDescriptorOutput(requestID).test()
        advanceTimeForWrites(1)

        then:
        closeresult.assertNoErrors()
        closeresult
                .assertNoErrors()
                .assertValue(new Predicate<byte[]>() {
                    @Override
                    boolean test(@NonNull byte[] bytes) throws Exception {
                        return Arrays.equals(bytes, finaldata)
                    }
                })
    }


    def "characteristicLongWriteOutput handles nonexistent requestid"() {
        when:
        prepCharacteristicLongWriteOutput()
        def closeresult = objectUnderTest.closeLongWriteCharacteristicOutput(0).test()
        advanceTimeForWrites(1)

        then:
        closeresult.assertNoErrors()
        closeresult.assertEmpty()

    }

    def "descriptorLongWriteOutput handles nonexistent requestid"() {
        when:
        prepDescriptorLongWriteOutput()
        def closeresult = objectUnderTest.closeLongWriteDescriptorOutput(0).test()
        advanceTimeForWrites(1)

        then:
        closeresult.assertNoErrors()
        closeresult.assertEmpty()

    }

    private prepCharacteristicLongWriteOutput() {
        def output = objectUnderTest.openLongWriteCharacteristicOutput(requestID, characteristic)
        advanceTimeForWrites(1)
        for (int x=0;x<repeat;x++) {
            output.valueRelay.onNext(data)
            advanceTimeForWrites(1)
        }
    }

    private prepDescriptorLongWriteOutput() {
        def output = objectUnderTest.openLongWriteDescriptorOutput(requestID, descriptor)
        advanceTimeForWrites(1)
        for (int x=0;x<repeat;x++) {
            output.valueRelay.onNext(data)
            advanceTimeForWrites(1)
        }
    }

    private advanceTimeForWrites(long numberOfWrites) {
        testScheduler.advanceTimeBy(numberOfWrites * DEFAULT_WRITE_DELAY, TimeUnit.SECONDS)
    }

    private advanceTimeForWritesToComplete(long numberOfWrites) {
        advanceTimeForWrites(numberOfWrites + 1)
    }


}