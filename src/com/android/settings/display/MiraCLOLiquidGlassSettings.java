/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.display;

import android.app.settings.SettingsEnums;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;

/** MiraCLO: Settings > Display > Liquid Glass: where SystemUI uses Liquid Glass. */
public class MiraCLOLiquidGlassSettings extends DashboardFragment {

    private static final String TAG = "MiraCLOLiquidGlassSettings";

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DISPLAY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.miraclo_liquid_glass_settings;
    }
}
