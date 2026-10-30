/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.display;

import android.content.Context;
import android.provider.Settings;

import com.android.settings.core.TogglePreferenceController;

/**
 * MiraCLO: the "Use Liquid Glass" bar at the top of Settings > Display > Liquid Glass.
 * Stored in Settings.Secure "miraclo_liquid_glass" (read by SystemUI), off by default.
 */
public class MiraCLOLiquidGlassMainSwitchPreferenceController extends TogglePreferenceController {

    private static final String KEY_ENABLED = "miraclo_liquid_glass";

    public MiraCLOLiquidGlassMainSwitchPreferenceController(Context context, String preferenceKey) {
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
