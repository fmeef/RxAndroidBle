package com.polidea.rxandroidble2;

public interface RxBleComponent {
    class NamedExecutors {

        public static final String BLUETOOTH_INTERACTION = "executor_bluetooth_interaction";
        public static final String CONNECTION_QUEUE = "executor_connection_queue";
        private NamedExecutors() {

        }
    }

    class NamedSchedulers {

        public static final String COMPUTATION = "computation";
        public static final String TIMEOUT = "timeout";
        public static final String BLUETOOTH_INTERACTION = "bluetooth_interaction";
        public static final String BLUETOOTH_CALLBACKS = "bluetooth_callbacks";
        private NamedSchedulers() {

        }
    }

    class PlatformConstants {

        public static final String INT_TARGET_SDK = "target-sdk";
        public static final String INT_DEVICE_SDK = "device-sdk";
        public static final String BOOL_IS_ANDROID_WEAR = "android-wear";
        public static final String STRING_ARRAY_SCAN_PERMISSIONS = "scan-permissions";
        private PlatformConstants() {

        }
    }

    class NamedBooleanObservables {

        public static final String LOCATION_SERVICES_OK = "location-ok-boolean-observable";
        private NamedBooleanObservables() {

        }
    }

    class BluetoothConstants {

        public static final String ENABLE_NOTIFICATION_VALUE = "enable-notification-value";
        public static final String ENABLE_INDICATION_VALUE = "enable-indication-value";
        public static final String DISABLE_NOTIFICATION_VALUE = "disable-notification-value";
        private BluetoothConstants() {

        }
    }
}
