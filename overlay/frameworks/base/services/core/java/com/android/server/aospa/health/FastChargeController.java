/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.aospa.health;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.util.ArrayUtils;

import vendor.aospa.health.IFastCharge;

import java.io.PrintWriter;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class FastChargeController extends AospaHealthFeature {
    private final int[] mChargingSpeedValues;
    private final ContentResolver mContentResolver;
    private final IFastCharge mFastCharge;
    private Integer mAppliedMode;
    private Exception mApplyFailure;

    // Settings uris
    private final Uri MODE_URI = Settings.System.getUriFor(
            Settings.System.FAST_CHARGE_MODE);

    public FastChargeController(Context context, Handler handler) {
        super(context, handler);

        mContentResolver = mContext.getContentResolver();
        mFastCharge = IFastCharge.Stub.asInterface(
                ServiceManager.waitForDeclaredService(
                        IFastCharge.DESCRIPTOR + "/default"));

        Resources res = mContext.getResources();
        mChargingSpeedValues = Stream.of(res.getStringArray(R.array.charging_speed_values))
                .mapToInt(Integer::parseInt)
                .toArray();

        if (mFastCharge == null) {
            Log.i(TAG, "Lineage Health HAL not found");
            return;
        }
    }

    @Override
    public boolean isSupported() {
        try {
            return mFastCharge != null && mFastCharge.getSupportedFastChargeModes() > 0;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public int[] getSupportedFastChargeModes() {
        if (mFastCharge == null) {
            return new int[0];
        }

        try {
            long supportedFastChargeModes = mFastCharge.getSupportedFastChargeModes();

            return IntStream.of(mChargingSpeedValues)
                    .filter(mode -> (supportedFastChargeModes & mode) != 0)
                    .toArray();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private int getRequestedMode() {
        int[] supportedFastChargeModes = getSupportedFastChargeModes();
        if (supportedFastChargeModes.length == 0) {
            throw new UnsupportedOperationException("Fast charge mode control unavailable");
        }
        int defaultMode = supportedFastChargeModes[supportedFastChargeModes.length - 1];

        int mode = getInt(Settings.System.FAST_CHARGE_MODE,
                defaultMode);
        if (mode != defaultMode && !ArrayUtils.contains(supportedFastChargeModes, mode)) {
            throw new IllegalArgumentException("Unsupported saved fast charge mode: " + mode);
        }

        return mode;
    }

    public synchronized int getFastChargeMode() {
        if (mApplyFailure != null) {
            throw new IllegalStateException("Fast charge configuration was not applied", mApplyFailure);
        }
        if (mAppliedMode == null) {
            throw new IllegalStateException("Fast charge configuration has not been applied yet");
        }
        return mAppliedMode;
    }

    private void applyMode(int mode) throws RemoteException {
        if (!ArrayUtils.contains(getSupportedFastChargeModes(), mode)) {
            throw new IllegalArgumentException("Unsupported fast charge mode: " + mode);
        }
        mFastCharge.setFastChargeMode(mode);
        mAppliedMode = mode; // A successful HAL operation, not merely a preference write.
        mApplyFailure = null;
    }

    public synchronized boolean setFastChargeMode(int mode) {
        try {
            applyMode(mode);
        } catch (RemoteException e) {
            mApplyFailure = e;
            throw e.rethrowFromSystemServer();
        } catch (ServiceSpecificException e) {
            mApplyFailure = e;
            throw e;
        }
        if (!Settings.System.putIntForUser(mContentResolver,
                Settings.System.FAST_CHARGE_MODE, mode, UserHandle.USER_CURRENT)) {
            throw new IllegalStateException("HAL applied mode, but saving the preference failed");
        }
        return true;
    }

    @Override
    public void onStart() {
        if (mFastCharge == null) {
            return;
        }

        // Register setting observer
        registerSettings(MODE_URI);

        handleSettingChange();
    }

    private synchronized void handleSettingChange() {
        try {
            final int requested = getRequestedMode();
            if (mAppliedMode != null && mAppliedMode == requested && mApplyFailure == null) return;
            applyMode(requested);
        } catch (RemoteException | RuntimeException e) {
            mApplyFailure = e;
            Log.e(TAG, "Fast charge setting application failed", e);
        }
    }

    @Override
    protected void onSettingsChanged(Uri uri) {
        handleSettingChange();
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println();
        pw.println("FastChargeController Configuration:");
        pw.println("  Applied mode: " + mAppliedMode);
        pw.println("  Apply failure: " + mApplyFailure);
        pw.println();
    }
}
