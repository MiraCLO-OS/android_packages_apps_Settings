/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.notification;

import android.app.settings.SettingsEnums;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;

/** MiraCLO: Settings > Notifications > Dynamic Island. */
public class MiraCLOIslandSettings extends DashboardFragment {

    private static final String TAG = "MiraCLOIslandSettings";

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.CONFIGURE_NOTIFICATION;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.miraclo_island_settings;
    }
}
