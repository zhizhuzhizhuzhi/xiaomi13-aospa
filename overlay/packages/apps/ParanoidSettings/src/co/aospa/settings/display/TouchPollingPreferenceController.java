/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.settings.display;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.provider.Settings;
import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;

/** Native Display switch; the device provider applies its stock touch profile. */
public class TouchPollingPreferenceController extends TogglePreferenceController {
    private static final String KEY = "touch_polling_rate";
    private static final String ACTION = "co.aospa.action.TOUCH_POLLING_RATE";

    public TouchPollingPreferenceController(Context context, String key) {
        super(context, key);
    }

    private ComponentName provider() {
        PackageManager pm = mContext.getPackageManager();
        for (ResolveInfo r : pm.queryIntentServices(new Intent(ACTION),
                PackageManager.MATCH_SYSTEM_ONLY)) {
            if (r.serviceInfo != null && pm.checkSignatures(mContext.getPackageName(),
                    r.serviceInfo.packageName) == PackageManager.SIGNATURE_MATCH) {
                return new ComponentName(r.serviceInfo.packageName, r.serviceInfo.name);
            }
        }
        return null;
    }

    @Override public int getAvailabilityStatus() {
        return provider() != null ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override public boolean isChecked() {
        return Settings.System.getIntForUser(mContext.getContentResolver(), KEY, 0,
                UserHandle.USER_CURRENT) == 1;
    }

    @Override public boolean setChecked(boolean checked) {
        ComponentName service = provider();
        if (service == null) return false;
        boolean previous = isChecked();
        if (!Settings.System.putIntForUser(mContext.getContentResolver(), KEY, checked ? 1 : 0,
                UserHandle.USER_CURRENT)) return false;
        try {
            if (mContext.startService(new Intent(ACTION).setComponent(service)) != null) return true;
        } catch (RuntimeException unavailable) {
            android.util.Log.e("TouchPolling", "Device touch provider unavailable", unavailable);
        }
        Settings.System.putIntForUser(mContext.getContentResolver(), KEY, previous ? 1 : 0,
                UserHandle.USER_CURRENT);
        return false;
    }

    @Override public int getSliceHighlightMenuRes() { return R.string.menu_key_display; }
}
