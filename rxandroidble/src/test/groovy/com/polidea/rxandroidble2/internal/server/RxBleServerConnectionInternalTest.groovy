package com.polidea.rxandroidble2.internal.server

import android.bluetooth.*
import android.content.Context
import bleshadow.javax.inject.Provider
import com.polidea.rxandroidble2.DummyOperationQueue
import com.polidea.rxandroidble2.ServerConfig
import com.polidea.rxandroidble2.ServerTransactionFactory
import com.polidea.rxandroidble2.Timeout
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProvider
import com.polidea.rxandroidble2.internal.operations.server.ServerConnectionOperationsProviderImpl
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueue
import com.polidea.rxandroidble2.internal.util.MockOperationTimeoutConfiguration
import io.reactivex.Flowable
import io.reactivex.annotations.NonNull
import io.reactivex.functions.Predicate
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.TestScheduler
import spock.lang.Specification

import java.util.concurrent.TimeUnit

public class RxBleServerConnectionInternalTest extends Specification {
    static final UUID CLIENT_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static long DEFAULT_WRITE_DELAY = 1
    UUID testUuid = UUID.randomUUID()
    TestScheduler testScheduler = new TestScheduler()
    def mockTimeout = new MockOperationTimeoutConfiguration(10, testScheduler)
    ServerConnectionOperationsProvider operationsProvider
    ServerOperationQueue dummyQueue = new DummyOperationQueue()
    BluetoothDevice bluetoothDevice = Mock BluetoothDevice
    BluetoothGattServer bluetoothGattServer = Mock BluetoothGattServer
    RxBleServerConnectionImpl objectUnderTest
    BluetoothManager bluetoothManager = Mock BluetoothManager
    ServerConfig config = ServerConfig.newInstance(Mock(Timeout))
    RxBleServerState serverState = Mock(RxBleServerState)
    ServerTransactionFactory serverTransactionFactory = Mock ServerTransactionFactory
    BluetoothGattCharacteristic characteristic = Mock BluetoothGattCharacteristic

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
                bluetoothManager,
                mockTimeout,
                new Provider<BluetoothGattServer>() {
                    @Override
                    BluetoothGattServer get() {
                        return bluetoothGattServer
                    }
                },
                new Provider<RxBleServerConnectionInternal>() {
                    @Override
                    RxBleServerConnectionInternal get() {
                        return objectUnderTest
                    }
                }
        )

        objectUnderTest = new RxBleServerConnectionImpl(
                testScheduler,
                testScheduler,
                operationsProvider,
                dummyQueue,
                bluetoothManager,
                serverTransactionFactory,
                config,
                Mock(Context),
                serverState
        )
    }

    def "notifications complete correctly"() {
        setup:
        BluetoothGattCharacteristic ch = Mock(BluetoothGattCharacteristic)

        when:
        def notif = Flowable.just(data).repeat(4);
        def indicationnotif = Flowable.just(data).repeat(4)
        TestObserver res = objectUnderTest.setupNotifications(ch.getUuid(), notif, Mock(BluetoothDevice)).test();
        TestObserver indicationres = objectUnderTest.setupIndication(ch.getUuid(), indicationnotif, Mock(BluetoothDevice)).test()
        for (int i=0;i<4*2;i++) {
            objectUnderTest.getNotificationPublishRelay().valueRelay.accept(BluetoothGatt.GATT_SUCCESS)
            advanceTimeForWritesToComplete(1)
        }

        then:

        2 * _.getCharacteristic(_) >> characteristic
        characteristic.getService() >> new BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        _.getDescriptor(_) >>  new BluetoothGattDescriptor(
                CLIENT_CONFIG,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED  | BluetoothGattDescriptor.PERMISSION_WRITE
        )

        serverState.getNotifications(_) >> true
        serverState.getIndications(_) >> true
        bluetoothGattServer.notifyCharacteristicChanged(_, _, _) >> true
        res.assertComplete()
        indicationres.assertComplete()
    }



    def "notifications complete when enabled"() {

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