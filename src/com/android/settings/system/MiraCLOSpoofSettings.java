/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.system;

import android.app.settings.SettingsEnums;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;

/**
 * MiraCLO: Settings > System > Spoof. The options that change what apps can see or do (hide
 * developer status, ignore the secure flag) in one place, moved from Developer options and
 * Security & privacy.
 */
public class MiraCLOSpoofSettings extends DashboardFragment {

    private static final String TAG = "MiraCLOSpoofSettings";

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_SYSTEM_CATEGORY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.miraclo_spoof_settings;
    }
}
