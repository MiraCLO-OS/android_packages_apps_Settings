package com.android.settings.deviceinfo;

import android.content.Context;
import android.os.SystemProperties;

import com.android.settings.core.BasePreferenceController;

public class MiraCLOVersionPreferenceController extends BasePreferenceController {

    private static final String PROP = "ro.aospa.miraclo.version";

    public MiraCLOVersionPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return SystemProperties.get(PROP).isEmpty() ? UNSUPPORTED_ON_DEVICE : AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        String v = SystemProperties.get(PROP); // e.g. 1.0-unofficial
        String[] p = v.split("-", 2);
        if (p.length < 2 || p[1].isEmpty()) return v;
        return p[0] + " " + Character.toUpperCase(p[1].charAt(0)) + p[1].substring(1);
    }
}
