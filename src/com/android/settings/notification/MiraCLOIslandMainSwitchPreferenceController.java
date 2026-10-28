/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.notification;

import android.content.Context;
import android.provider.Settings;

import com.android.settings.core.TogglePreferenceController;

/**
 * MiraCLO: the "Use Dynamic Island" bar at the top of Settings > Notifications > Dynamic Island.
 * Stored in Settings.Secure "miraclo_island_enabled" (read by SystemUI), off by default.
 */
public class MiraCLOIslandMainSwitchPreferenceController extends TogglePreferenceController {

    private static final String KEY_ENABLED = "miraclo_island_enabled";

    public MiraCLOIslandMainSwitchPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public boolean isChecked() {
        return Settings.Secure.getInt(mContext.getContentResolver(), KEY_ENABLED, 0) != 0;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return Settings.Secure.putInt(mContext.getContentResolver(), KEY_ENABLED,
                isChecked ? 1 : 0);
    }

    @Override
    public boolean isSliceable() {
        return false;
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return 0;
    }
}
