/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.custom.font

import android.content.Context
import android.util.AttributeSet
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.android.settings.R
import kotlinx.coroutines.launch

class CustomFontPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {

    private var fontPickerLauncher: ActivityResultLauncher<Array<String>>? = null

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
        super.onAttachedToHierarchy(preferenceManager)
        val activity = context as? FragmentActivity ?: return
        fontPickerLauncher = activity.activityResultRegistry.register(
            "font_picker_$key",
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let {
                activity.lifecycleScope.launch {
                    if (ExternalFontInstaller(activity).installFontFromUri(it) != null) {
                        showRebootDialog()
                    }
                }
            }
        }
    }

    override fun onDetached() {
        fontPickerLauncher?.unregister()
        fontPickerLauncher = null
        super.onDetached()
    }

    override fun onClick() {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setPositiveButton(R.string.add_font) { _, _ ->
                fontPickerLauncher?.launch(arrayOf("font/ttf", "font/otf"))
            }
            .setNeutralButton(R.string.reset_font) { _, _ ->
                ExternalFontInstaller(context).resetFontUpdates()
                showRebootDialog()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRebootDialog() {
        AlertDialog.Builder(context)
            .setTitle(R.string.reboot_required_title)
            .setMessage(R.string.reboot_required_message)
            .setPositiveButton(R.string.action_reboot_now) { _, _ ->
                ExternalFontInstaller.rebootDevice()
            }
            .setNegativeButton(R.string.action_later, null)
            .show()
    }
}
