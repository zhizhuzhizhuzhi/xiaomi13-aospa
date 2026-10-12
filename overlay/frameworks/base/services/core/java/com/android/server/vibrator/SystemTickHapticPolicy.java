/* SPDX-License-Identifier: Apache-2.0 */
package com.android.server.vibrator;

import android.os.CombinedVibration;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;

/** Consistent predefined tick for short SystemUI and overview interactions. */
final class SystemTickHapticPolicy {
    private SystemTickHapticPolicy() {}

    static CombinedVibration apply(String packageName, CombinedVibration effect,
            VibrationAttributes attrs) {
        if (!"com.android.systemui".equals(packageName)
                && !"com.android.launcher3".equals(packageName)) {
            return effect;
        }
        int usage = attrs.getUsage();
        if (usage != VibrationAttributes.USAGE_TOUCH
                && usage != VibrationAttributes.USAGE_HARDWARE_FEEDBACK
                && usage != VibrationAttributes.USAGE_GESTURE_INPUT) {
            return effect;
        }
        long duration = effect.getDuration();
        if (effect.hasVendorEffects() || duration == Long.MAX_VALUE || duration > 150) {
            return effect;
        }
        return CombinedVibration.createParallel(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
    }
}
