/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.aospa.health;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.android.internal.lineage.app.LineageContextConstants;

public class HealthInterface {
    /**
     * No config set. This value is invalid and does not have any effects
     */
    public static final int MODE_NONE = 0;

    /**
     * Automatic config
     */
    public static final int MODE_AUTO = 1;

    /**
     * Manual config mode
     */
    public static final int MODE_MANUAL = 2;

    /**
     * Limit config mode
     */
    public static final int MODE_LIMIT = 3;

    private static final String TAG = "HealthInterface";
    private static IHealthInterface sService;
    private static HealthInterface sInstance;
    private Context mContext;
    private HealthInterface(Context context) {
        Context appContext = context.getApplicationContext();
        mContext = appContext == null ? context : appContext;
        sService = getService();
    }
    /**
     * Get or create an instance of the {@link lineageos.health.HealthInterface}
     *
     * @param context Used to get the service
     * @return {@link HealthInterface}
     */
    public static synchronized HealthInterface getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new HealthInterface(context);
        }
        return sInstance;
    }
    /** @hide **/
    public static IHealthInterface getService() {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(LineageContextConstants.AOSPA_HEALTH_INTERFACE);
        sService = IHealthInterface.Stub.asInterface(b);
        if (sService == null) {
            throw new IllegalStateException("AOSPA health service unavailable");
        }
        return sService;
    }

    /**
     * @return true if service is valid
     */
    private IHealthInterface requireService() {
        return getService();
    }

    /**
     * Returns whether charging control is supported
     *
     * @return true if charging control is supported
     */
    public boolean isChargingControlSupported() {
        try {
            return requireService().isChargingControlSupported();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns whether charging control is supported
     *
     * @return true if charging control is supported
     */
    public static boolean isChargingControlSupported(Context context) {
        return getInstance(context).isChargingControlSupported();
    }

    /**
     * Returns the charging control enabled status
     *
     * @return whether charging control has been enabled
     */
    public boolean getEnabled() {
        try {
            return requireService().getChargingControlEnabled();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Set charging control enable status
     *
     * @param enabled whether charging control should be enabled
     * @return true if the enabled status was successfully set
     */
    public boolean setEnabled(boolean enabled) {
        try {
            return requireService().setChargingControlEnabled(enabled);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the current charging control mode
     *
     * @return id of the charging control mode
     */
    public int getMode() {
        try {
            return requireService().getChargingControlMode();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Selects the new charging control mode
     *
     * @param mode the new charging control mode
     * @return true if the mode was successfully set
     */
    public boolean setMode(int mode) {
        try {
            return requireService().setChargingControlMode(mode);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the charging control start time
     *
     * @return the seconds of the day of the start time
     */
    public int getStartTime() {
        try {
            return requireService().getChargingControlStartTime();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the charging control start time
     *
     * @param time the seconds of the day of the start time
     * @return true if the start time was successfully set
     */
    public boolean setStartTime(int time) {
        try {
            return requireService().setChargingControlStartTime(time);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the charging control target time
     *
     * @return the seconds of the day of the target time
     */
    public int getTargetTime() {
        try {
            return requireService().getChargingControlTargetTime();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the charging control target time
     *
     * @param time the seconds of the day of the target time
     * @return true if the target time was successfully set
     */
    public boolean setTargetTime(int time) {
        try {
            return requireService().setChargingControlTargetTime(time);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the charging control limit
     *
     * @return the charging control limit
     */
    public int getLimit() {
        try {
            return requireService().getChargingControlLimit();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the charging control limit
     *
     * @param limit the charging control limit
     * @return true if the limit was successfully set
     */
    public boolean setLimit(int limit) {
        try {
            return requireService().setChargingControlLimit(limit);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Resets the charging control setting to default
     *
     * @return true if the setting was successfully reset
     */
    public boolean reset() {
        try {
            return requireService().resetChargingControl();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns whether the device's battery control bypasses battery
     *
     * @return true if the charging control bypasses battery
     */
    public boolean allowFineGrainedSettings() {
        try {
            return requireService().allowFineGrainedSettings();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns whether fast charge is supported
     *
     * @return true if fast charge is supported
     */
    public boolean isFastChargeSupported() {
        try {
            return requireService().isFastChargeSupported();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets supported fast charge mode
     *
     * @return true supported fast charge modes
     */
    public int[] getSupportedFastChargeModes() {
        try {
            return requireService().getSupportedFastChargeModes();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets current fast charge mode
     *
     * @return true current fast charge mode
     */
    public int getFastChargeMode() {
        try {
            return requireService().getFastChargeMode();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets selected fast charge mode
     *
     * @param mode the fast charge mode
     * @return true if fast charge was set
     */
    public boolean setFastChargeMode(int mode) {
        try {
            return requireService().setFastChargeMode(mode);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
