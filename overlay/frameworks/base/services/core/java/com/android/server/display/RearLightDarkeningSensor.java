/* SPDX-License-Identifier: Apache-2.0 */
package com.android.server.display;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.util.Slog;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Event-only, optional rear-ALS protection for the native front-light controller. */
final class RearLightDarkeningSensor {
    private static final String TAG = "RearLightDarkeningSensor";
    private final RearLightDarkeningGuard mGuard = new RearLightDarkeningGuard();
    private final SensorManager mManager;
    private final CameraManager mCamera;
    private final Sensor mRear;
    private final Handler mHandler;
    private final Runnable mReleased;
    private final LongSupplier mClock;
    private final Set<String> mFlashCameras = new HashSet<>();
    private final Set<String> mInterferingCameras = new HashSet<>();
    private final Set<String> mAwaitingTorchCameras = new HashSet<>();
    private boolean mEnabled;
    private boolean mTorchRegistered;
    private int mGeneration;
    private SensorEventListener mListener;
    private CameraManager.TorchCallback mTorch;
    private long mPendingRearTime;
    private float mPendingRearLux = Float.NaN;

    static RearLightDarkeningSensor create(Context context, SensorManager manager,
            Handler handler, LongSupplier clock, Runnable released) {
        int type = context.getResources().getInteger(
                com.android.internal.R.integer.config_rearLightDarkeningGuardSensorType);
        if (type == 0) return null;
        Sensor rear = manager.getDefaultSensor(type, false);
        CameraManager camera = context.getSystemService(CameraManager.class);
        // Stable ON_CHANGE values are usable without periodically sampling them.
        if (rear == null || rear.getReportingMode() != Sensor.REPORTING_MODE_ON_CHANGE
                || camera == null) {
            Slog.w(TAG, "Rear protection unavailable; retaining native front ALS");
            return null;
        }
        try {
            return new RearLightDarkeningSensor(manager, camera, rear, handler, clock, released);
        } catch (Exception e) {
            Slog.w(TAG, "Cannot inspect torch interference; retaining native front ALS", e);
            return null;
        }
    }

    private RearLightDarkeningSensor(SensorManager manager, CameraManager camera,
            Sensor rear, Handler handler, LongSupplier clock, Runnable released) throws Exception {
        mManager = manager; mCamera = camera; mRear = rear; mHandler = handler;
        mClock = clock; mReleased = released;
        for (String id : camera.getCameraIdList()) {
            CameraCharacteristics c = camera.getCameraCharacteristics(id);
            Integer lens = c.get(CameraCharacteristics.LENS_FACING);
            if (lens != null && lens == CameraCharacteristics.LENS_FACING_BACK
                    && Boolean.TRUE.equals(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))) {
                mFlashCameras.add(id);
            }
        }
    }

    void setEnabled(boolean enabled, int rateMillis) {
        if (enabled == mEnabled) return;
        if (!enabled) {
            mEnabled = false;
            mGeneration++;
            if (mListener != null) mManager.unregisterListener(mListener);
            mListener = null;
            if (mTorchRegistered) mCamera.unregisterTorchCallback(mTorch);
            mTorchRegistered = false;
            mTorch = null;
            mInterferingCameras.clear();
            mAwaitingTorchCameras.clear();
            mGuard.reset();
            return;
        }
        mGuard.reset();
        mEnabled = true;
        mAwaitingTorchCameras.addAll(mFlashCameras);
        mPendingRearTime = 0;
        mPendingRearLux = Float.NaN;
        final int generation = ++mGeneration;
        mListener = createRearListener(generation);
        mTorch = createTorchCallback(generation);
        // Unknown startup torch state gates decisions in the adapter. It is not
        // an actual torch-on event and must not consume the ON_CHANGE initial sample.
        try {
            mCamera.registerTorchCallback(mTorch, mHandler);
            mTorchRegistered = true;
            if (!mManager.registerListener(mListener, mRear, rateMillis * 1000, mHandler)) {
                throw new IllegalStateException("Rear sensor registration rejected");
            }
        } catch (RuntimeException e) {
            setEnabled(false, rateMillis);
            Slog.w(TAG, "Rear protection registration failed; retaining native front ALS", e);
        }
    }

    void onFrontSample(long time, float lux, float acceptedLux) {
        if (mEnabled) mGuard.onFrontSample(time, lux, acceptedLux);
    }

    boolean shouldDeferDarkening(long time) {
        return mEnabled && mAwaitingTorchCameras.isEmpty()
                && mGuard.shouldDeferDarkening(time);
    }

    void dump(PrintWriter writer) {
        writer.println("  RearLightDarkeningSensor: enabled=" + mEnabled + ", sensor=" + mRear);
        writer.println("  " + mGuard);
    }

    private SensorEventListener createRearListener(final int generation) {
        return new SensorEventListener() {
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        @Override public void onSensorChanged(SensorEvent event) {
            if (!mEnabled || generation != mGeneration || event.values.length == 0) return;
            long time = TimeUnit.NANOSECONDS.toMillis(event.timestamp);
            // SensorService may deliver a valid cached ON_CHANGE initial value
            // with an older timestamp. Per-registration listeners reject OLD
            // callbacks without mistaking that initial value for a stale callback.
            mPendingRearTime = time;
            mPendingRearLux = event.values[0];
            if (!mAwaitingTorchCameras.isEmpty()) return;
            boolean wasHolding = mGuard.isHolding();
            mGuard.onRearSample(time, event.values[0]);
            if (wasHolding && !mGuard.isHolding()) mReleased.run();
        }
        };
    }

    private CameraManager.TorchCallback createTorchCallback(final int generation) {
        return new CameraManager.TorchCallback() {
        @Override public void onTorchModeChanged(String id, boolean enabled) {
            if (generation == mGeneration) interference(id, enabled);
        }
        @Override public void onTorchModeUnavailable(String id) {
            // Includes camera use that makes the flash state unavailable.
            if (generation == mGeneration) interference(id, true);
        }
        };
    }

    private void interference(String id, boolean active) {
        if (!mEnabled || !mFlashCameras.contains(id)) return;
        mAwaitingTorchCameras.remove(id);
        if (active) mInterferingCameras.add(id); else mInterferingCameras.remove(id);
        boolean wasHolding = mGuard.isHolding();
        boolean interfering = !mInterferingCameras.isEmpty();
        mGuard.setInterference(mClock.getAsLong(), interfering);
        if (mAwaitingTorchCameras.isEmpty() && !interfering) {
            // Replay the initial ON_CHANGE sample once state is known. A sample
            // from an actual torch interval is still rejected by the guard's timestamp.
            mGuard.onRearSample(mPendingRearTime, mPendingRearLux);
        }
        if (wasHolding && !mGuard.isHolding()) mReleased.run();
    }
}
