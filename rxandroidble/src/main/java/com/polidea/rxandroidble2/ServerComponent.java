package com.polidea.rxandroidble2;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.polidea.rxandroidble2.internal.DeviceComponent;
import com.polidea.rxandroidble2.internal.connection.ServerConnector;
import com.polidea.rxandroidble2.internal.connection.ServerConnectorImpl;
import com.polidea.rxandroidble2.internal.serialization.RxBleThreadFactory;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueue;
import com.polidea.rxandroidble2.internal.serialization.ServerOperationQueueImpl;
import com.polidea.rxandroidble2.internal.server.BluetoothGattServerProvider;
import com.polidea.rxandroidble2.internal.server.RxBleServerState;
import com.polidea.rxandroidble2.internal.server.RxBleServerStateImpl;

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

@ServerScope
@Component(modules = {ServerComponent.ServerModule.class})
public interface ServerComponent {

    String SERVER_CONTEXT = "server-context";

    class NamedSchedulers {
        public static final String BLUETOOTH_SERVER = "server_computation";
        public static final String BLUETOOTH_CONNECTION = "server_connection";
        public static final String TIMEOUT = "server_timeout";
        private NamedSchedulers() {

        }
    }

    class NamedExecutors {

        public static final String BLUETOOTH_INTERACTION = "executor_bluetooth_interaction_server";
        private NamedExecutors() {

        }
    }

    class PlatformConstants {

        public static final String INT_TARGET_SDK = "target-sdk";
        public static final String INT_DEVICE_SDK = "device-sdk";
        public static final String BOOL_IS_ANDROID_WEAR = "android-wear";
        private PlatformConstants() {

        }
    }

    class BluetoothConstants {

        public static final String ENABLE_NOTIFICATION_VALUE = "enable-notification-value";
        public static final String ENABLE_INDICATION_VALUE = "enable-indication-value";
        public static final String DISABLE_NOTIFICATION_VALUE = "disable-notification-value";

        private BluetoothConstants() {

        }
    }

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
        @Named(PlatformConstants.INT_DEVICE_SDK)
        static int provideDeviceSdk() {
            return Build.VERSION.SDK_INT;
        }

        @Provides
        static ContentResolver provideContentResolver(Context context) {
            return context.getContentResolver();
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

        @Provides
        @Named(NamedSchedulers.BLUETOOTH_SERVER)
        @ServerScope
        static Scheduler provideBluetoothServerScheduler() {
            return RxJavaPlugins.createSingleScheduler(new RxBleThreadFactory());
        }

        @Provides
        @Named(NamedSchedulers.BLUETOOTH_CONNECTION)
        static Scheduler provideBluetoothConnectionScheduler() {
            return RxJavaPlugins.createSingleScheduler(new RxBleThreadFactory());
        }

        @Provides
        @Named(NamedSchedulers.TIMEOUT)
        static Scheduler providesBluetoothTimeoutScheduler() {
            return RxJavaPlugins.createSingleScheduler(new RxBleThreadFactory());
        }


        @Provides
        @Named(ServerComponent.NamedExecutors.BLUETOOTH_INTERACTION)
        static ExecutorService provideBluetoothInteractionExecutorService() {
            return Executors.newSingleThreadExecutor();
        }

        @Provides
        static BluetoothGattServer provideBluetoothGattServer(BluetoothGattServerProvider bluetoothGattServerProvider) {
            return bluetoothGattServerProvider.getBluetoothGatt();
        }

        @Binds
        abstract Observable<RxBleAdapterStateObservable.BleAdapterState> bindStateObs(RxBleAdapterStateObservable stateObservable);

        @Binds
        @ServerScope
        abstract RxBleServer bindRxBleServer(RxBleServerImpl rxBleServer);

        @Binds
        @ServerScope
        abstract ServerConnector bindServerConnector(ServerConnectorImpl serverConnector);

        @Binds
        @ServerScope
        abstract RxBleServerState bindServerState(RxBleServerStateImpl serverState);

        @Binds
        abstract ServerOperationQueue bindServerOperationQueue(ServerOperationQueueImpl operationQueue);
    }

    RxBleServer rxBleServer();

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    interface ServerComponentFinalizer {

        void onFinalize();
    }

}
