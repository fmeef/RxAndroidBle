package com.polidea.rxandroidble2;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.polidea.rxandroidble2.internal.DeviceComponent;
import com.polidea.rxandroidble2.internal.connection.ServerConnector;
import com.polidea.rxandroidble2.internal.connection.ServerConnectorImpl;
import com.polidea.rxandroidble2.internal.operations.OperationsProvider;
import com.polidea.rxandroidble2.internal.operations.OperationsProviderImpl;
import com.polidea.rxandroidble2.internal.scan.BackgroundScannerImpl;
import com.polidea.rxandroidble2.internal.serialization.ClientOperationQueue;
import com.polidea.rxandroidble2.internal.serialization.ClientOperationQueueImpl;
import com.polidea.rxandroidble2.internal.serialization.RxBleThreadFactory;
import com.polidea.rxandroidble2.scan.BackgroundScanner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bleshadow.dagger.Binds;
import bleshadow.dagger.BindsInstance;
import bleshadow.dagger.Component;
import bleshadow.dagger.Module;
import bleshadow.dagger.Provides;
import bleshadow.javax.inject.Named;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;

@ServerScope
@Component(modules = {ServerComponent.ServerModule.class})
public interface ServerComponent extends RxBleComponent {

    String SERVER_CONTEXT = "server-context";

    @Component.Builder
    interface Builder {
        ServerComponent build();

        @BindsInstance
        Builder applicationContext(Context context);
    }

    @Module(subcomponents = {DeviceComponent.class, ServerConnectionComponent.class})
    abstract class ServerModule {

        @Provides
        static BluetoothManager provideBluetoothManager(Context context) {
            return (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        }

        @Provides
        @Named(SERVER_CONTEXT)
        static Context provideContext(Context context) {
            return context;
        }

        @Provides
        @Nullable
        static BluetoothAdapter provideBluetoothAdapter() {
            return BluetoothAdapter.getDefaultAdapter();
        }

        @Provides
        @Named(NamedSchedulers.COMPUTATION)
        static Scheduler provideComputationScheduler() {
            return Schedulers.computation();
        }

        @Provides
        @Named(PlatformConstants.INT_DEVICE_SDK)
        static int provideDeviceSdk() {
            return Build.VERSION.SDK_INT;
        }

        @Provides
        static ContentResolver provideContentResolver(Context context) {
            return context.getContentResolver();
        }

        @Provides
        @Named(NamedExecutors.BLUETOOTH_INTERACTION)
        @ServerScope
        static ExecutorService provideBluetoothInteractionExecutorService() {
            return Executors.newSingleThreadExecutor();
        }

        @Provides
        @Named(NamedSchedulers.BLUETOOTH_INTERACTION)
        @ServerScope
        static Scheduler provideBluetoothInteractionScheduler(@Named(NamedExecutors.BLUETOOTH_INTERACTION) ExecutorService service) {
            return Schedulers.from(service);
        }

        @Provides
        @Named(NamedSchedulers.BLUETOOTH_CALLBACKS)
        @ServerScope
        static Scheduler provideBluetoothCallbacksScheduler() {
            return RxJavaPlugins.createSingleScheduler(new RxBleThreadFactory());
        }

        @Provides
        static ServerComponent.ServerComponentFinalizer provideFinalizationCloseable(
                @Named(NamedExecutors.BLUETOOTH_INTERACTION) final ExecutorService interactionExecutorService,
                @Named(NamedSchedulers.BLUETOOTH_CALLBACKS) final Scheduler callbacksScheduler
        ) {
            return new ServerComponent.ServerComponentFinalizer() {
                @Override
                public void onFinalize() {
                    interactionExecutorService.shutdown();
                    callbacksScheduler.shutdown();
                }
            };
        }

        @Provides
        static LocationManager provideLocationManager(Context context) {
            return (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        }

        @Provides
        @Named(PlatformConstants.INT_TARGET_SDK)
        static int provideTargetSdk(Context context) {
            try {
                return context.getPackageManager().getApplicationInfo(context.getPackageName(), 0).targetSdkVersion;
            } catch (Throwable catchThemAll) {
                return Integer.MAX_VALUE;
            }
        }

        @Provides
        @Named(PlatformConstants.BOOL_IS_ANDROID_WEAR)
        @SuppressLint("InlinedApi")
        static boolean provideIsAndroidWear(Context context, @Named(PlatformConstants.INT_DEVICE_SDK) int deviceSdk) {
            return deviceSdk >= Build.VERSION_CODES.KITKAT_WATCH
                    && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
        }

        @Provides
        @Named(BluetoothConstants.ENABLE_NOTIFICATION_VALUE)
        static byte[] provideEnableNotificationValue() {
            return BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
        }

        @Provides
        @Named(BluetoothConstants.ENABLE_INDICATION_VALUE)
        static byte[] provideEnableIndicationValue() {
            return BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
        }

        @Provides
        @Named(BluetoothConstants.DISABLE_NOTIFICATION_VALUE)
        static byte[] provideDisableNotificationValue() {
            return BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
        }

        @Binds
        abstract Observable<RxBleAdapterStateObservable.BleAdapterState> bindStateObs(RxBleAdapterStateObservable stateObservable);

        @Binds
        abstract BackgroundScanner bindBackgroundScanner(BackgroundScannerImpl backgroundScannerImpl);

        @Binds
        @ServerScope
        abstract RxBleServer bindRxBleServer(RxBleServerImpl rxBleServer);

        @Binds
        @ServerScope
        abstract ClientOperationQueue bindClientOperationQueue(ClientOperationQueueImpl clientOperationQueue);

        @Binds
        @ServerScope
        abstract ServerConnector bindServerConnector(ServerConnectorImpl serverConnector);

        @Binds
        abstract OperationsProvider bindRxBleOperationsProvider(OperationsProviderImpl operationsProvider);

        @Binds
        @Named(NamedSchedulers.TIMEOUT)
        abstract Scheduler bindTimeoutScheduler(@Named(NamedSchedulers.COMPUTATION) Scheduler computationScheduler);
    }

    RxBleServer rxBleServer();

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    interface ServerComponentFinalizer {

        void onFinalize();
    }

}
