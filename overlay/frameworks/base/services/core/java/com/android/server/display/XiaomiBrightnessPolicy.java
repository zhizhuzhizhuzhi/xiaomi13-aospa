package com.android.server.display;

/** Loads the required device policy; an implementation failure is never a frontend-only mode. */
final class XiaomiBrightnessPolicy {
    private final Object delegate;
    private final java.lang.reflect.Method enabled;
    private final java.lang.reflect.Method dump;

    private XiaomiBrightnessPolicy(Object instance, Class<?> type) throws ReflectiveOperationException {
        delegate = instance;
        enabled = type.getMethod("setEnabled", boolean.class);
        dump = type.getMethod("dump");
    }

    static XiaomiBrightnessPolicy create(android.content.Context context,
            android.hardware.SensorManager sensors, android.hardware.Sensor light,
            android.os.Handler handler, int rate, int warm, int shortHorizon, int longHorizon,
            long brighten, long darken, java.util.function.Consumer<float[]> callback) {
        if (!context.getResources().getBoolean(
                com.android.internal.R.bool.config_useXiaomiDualAlsPolicy)) return null;
        try {
            ClassLoader loader = new dalvik.system.PathClassLoader(
                    "/system_ext/framework/PuddingStockBrightness.jar",
                    XiaomiBrightnessPolicy.class.getClassLoader());
            Class<?> type = Class.forName("com.xiaomi.pudding.brightness.stock.Entry", true, loader);
            Object instance = type.getConstructor(android.content.Context.class,
                    android.hardware.SensorManager.class, android.hardware.Sensor.class,
                    android.os.Handler.class, int[].class, long[].class,
                    java.util.function.Consumer.class).newInstance(context, sensors, light, handler,
                    new int[]{rate, warm, shortHorizon, longHorizon, 4475},
                    new long[]{brighten, darken}, callback);
            return new XiaomiBrightnessPolicy(instance, type);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("Required Xiaomi dual-ALS policy could not load", e);
        }
    }

    void setEnabled(boolean value) {
        try {
            enabled.invoke(delegate, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Xiaomi dual-ALS lifecycle operation failed", e);
        }
    }

    void dump(java.io.PrintWriter writer) {
        try {
            writer.println(dump.invoke(delegate));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Xiaomi dual-ALS state could not be read", e);
        }
    }
}
