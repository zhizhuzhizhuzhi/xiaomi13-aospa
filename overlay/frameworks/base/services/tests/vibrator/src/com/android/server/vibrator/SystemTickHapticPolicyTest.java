/* SPDX-License-Identifier: Apache-2.0 */
package com.android.server.vibrator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import android.os.CombinedVibration;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import org.junit.Test;

public class SystemTickHapticPolicyTest {
    private static VibrationAttributes attrs(int usage) {
        return new VibrationAttributes.Builder().setUsage(usage).build();
    }
    private static CombinedVibration tick() {
        return CombinedVibration.createParallel(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
    }
    @Test public void uiPrimitivesAndFingerprintFeedbackUsePredefinedTick() {
        CombinedVibration original = CombinedVibration.createParallel(
                VibrationEffect.startComposition().addPrimitive(
                        VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.2f).compose());
        assertEquals(tick(), SystemTickHapticPolicy.apply("com.android.systemui", original,
                attrs(VibrationAttributes.USAGE_TOUCH)));
        assertEquals(tick(), SystemTickHapticPolicy.apply("com.android.systemui", original,
                attrs(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)));
        assertEquals(tick(), SystemTickHapticPolicy.apply("com.android.launcher3", original,
                attrs(VibrationAttributes.USAGE_TOUCH)));
    }
    @Test public void notificationsAndOtherAppsKeepTheirEffects() {
        CombinedVibration original = CombinedVibration.createParallel(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK));
        assertSame(original, SystemTickHapticPolicy.apply("com.android.systemui", original,
                attrs(VibrationAttributes.USAGE_NOTIFICATION)));
        assertSame(original, SystemTickHapticPolicy.apply("com.android.systemui", original,
                attrs(VibrationAttributes.USAGE_RINGTONE)));
        assertSame(original, SystemTickHapticPolicy.apply("com.example.app", original,
                attrs(VibrationAttributes.USAGE_TOUCH)));
    }
    @Test public void sustainedAndRepeatingVibrationsArePreserved() {
        CombinedVibration original = CombinedVibration.createParallel(
                VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
        assertSame(original, SystemTickHapticPolicy.apply("com.android.systemui", original,
                attrs(VibrationAttributes.USAGE_TOUCH)));
        original = CombinedVibration.createParallel(
                VibrationEffect.createWaveform(new long[]{0, 10, 10, 10}, 0));
        assertSame(original, SystemTickHapticPolicy.apply("com.android.systemui", original,
                attrs(VibrationAttributes.USAGE_TOUCH)));
    }
}
