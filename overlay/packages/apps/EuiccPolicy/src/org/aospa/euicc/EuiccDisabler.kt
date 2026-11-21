/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.aospa.euicc

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ApplicationInfoFlags

object EuiccDisabler {
    private val EUICC_PACKAGES = listOf("com.google.android.euicc")

    private fun isInstalled(pm: PackageManager, pkgName: String) =
        runCatching {
                val info = pm.getApplicationInfo(pkgName, ApplicationInfoFlags.of(0))
                info.flags and ApplicationInfo.FLAG_INSTALLED != 0
            }
            .getOrDefault(false)

    fun enableOrDisableEuicc(context: Context) {
        val pm = context.packageManager
        val supportsEuicc = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_EUICC)
        val flag =
            if (!supportsEuicc) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }

        for (pkg in EUICC_PACKAGES) {
            if (isInstalled(pm, pkg)) {
                pm.setApplicationEnabledSetting(pkg, flag, 0)
            }
        }
    }
}
