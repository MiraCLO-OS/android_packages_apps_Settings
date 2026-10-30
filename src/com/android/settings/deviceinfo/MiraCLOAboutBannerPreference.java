/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo;

import android.content.Context;
import android.util.AttributeSet;

import com.android.settingslib.widget.LayoutPreference;
import com.android.settingslib.widget.NormalPaddingMixin;

/**
 * MiraCLO: holds the About phone banner. No card background and no side padding from the
 * list, so the banner lines up with the cards below it.
 */
public class MiraCLOAboutBannerPreference extends LayoutPreference implements NormalPaddingMixin {

    public MiraCLOAboutBannerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
}
