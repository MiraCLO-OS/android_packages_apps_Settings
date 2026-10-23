/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.custom.font

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.android.settings.R
import com.android.settingslib.spa.framework.theme.SettingsTheme
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
            if (uri != null) previewFont(activity, uri)
        }
        updateFontSummary()
    }

    override fun onDetached() {
        fontPickerLauncher?.unregister()
        fontPickerLauncher = null
        super.onDetached()
    }

    override fun onClick() {
        val activity = context as? FragmentActivity ?: return
        val installer = ExternalFontInstaller(activity)
        showFontDialog(activity, installer, previewTypeface = installer.loadInstalledTypeface())
    }

    private fun updateFontSummary(name: String? = ExternalFontInstaller(context).installedFontName) {
        summary = name?.let { context.getString(R.string.custom_font_applied_summary, it) }
            ?: context.getString(R.string.custom_font_summary)
    }

    private fun previewFont(activity: FragmentActivity, uri: Uri) {
        val installer = ExternalFontInstaller(activity)
        activity.lifecycleScope.launch {
            val typeface = installer.loadTypefaceFromUri(uri)
            if (typeface == null) {
                Toast.makeText(activity, R.string.toast_invalid_font_file, Toast.LENGTH_SHORT).show()
                installer.cleanupPreviewFont()
                return@launch
            }
            val name = installer.getDisplayName(uri)
                ?: activity.getString(R.string.custom_font_selected_file)
            showFontDialog(activity, installer, uri, name, typeface)
        }
    }

    private fun showFontDialog(
        activity: FragmentActivity,
        installer: ExternalFontInstaller,
        selectedUri: Uri? = null,
        selectedName: String? = null,
        previewTypeface: Typeface? = null,
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                SettingsTheme {
                    FontPickerDialogContent(
                        appliedFontName = installer.installedFontName,
                        selectedFontName = selectedName,
                        previewTypeface = previewTypeface,
                        onChooseFont = {
                            dialog.dismiss()
                            fontPickerLauncher?.launch(arrayOf("font/ttf", "font/otf"))
                        },
                        onApply = {
                            val uri = selectedUri ?: return@FontPickerDialogContent
                            dialog.dismiss()
                            activity.lifecycleScope.launch {
                                val appliedName = installer.installFontFromUri(uri)
                                if (appliedName != null) {
                                    updateFontSummary(appliedName)
                                    showRebootDialog()
                                }
                            }
                        },
                        onReset = {
                            dialog.dismiss()
                            androidx.appcompat.app.AlertDialog.Builder(activity)
                                .setTitle(R.string.reset_font)
                                .setMessage(R.string.reset_font_confirmation)
                                .setPositiveButton(R.string.reset_font_and_reboot) { _, _ ->
                                    activity.lifecycleScope.launch {
                                        if (installer.resetFontUpdates()) {
                                            updateFontSummary(null)
                                            ExternalFontInstaller.rebootDevice()
                                        } else {
                                            Toast.makeText(
                                                activity,
                                                R.string.toast_failed_apply_font,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        },
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.setOnDismissListener {
            if (selectedUri != null) installer.cleanupPreviewFont()
        }
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        val density = activity.resources.displayMetrics.density
        val maxWidth =
            (activity.resources.displayMetrics.widthPixels - 32 * density)
                .toInt()
                .coerceAtLeast((280 * density).toInt())
        dialog.window?.setLayout(
            minOf((480 * density).toInt(), maxWidth),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun showRebootDialog() {
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.reboot_required_title)
            .setMessage(R.string.reboot_required_message)
            .setPositiveButton(R.string.action_reboot_now) { _, _ ->
                ExternalFontInstaller.rebootDevice()
            }
            .setNegativeButton(R.string.action_later, null)
            .show()
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun FontPickerDialogContent(
    appliedFontName: String?,
    selectedFontName: String?,
    previewTypeface: Typeface?,
    onChooseFont: () -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
) {
    val previewLabel = when {
        selectedFontName != null -> stringResource(R.string.custom_font_preview_selected, selectedFontName)
        appliedFontName != null -> stringResource(R.string.custom_font_applied_summary, appliedFontName)
        else -> stringResource(R.string.custom_font_no_font_applied)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.custom_font_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
                Text(
                    text = stringResource(R.string.custom_font_summary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Keep the active or newly selected font visible as the screen's primary preview.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = previewLabel,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = stringResource(R.string.custom_font_preview_sample),
                        fontFamily = previewTypeface?.let { FontFamily(it) },
                        style = MaterialTheme.typography.displaySmallEmphasized,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.custom_font_preview_body),
                        fontFamily = previewTypeface?.let { FontFamily(it) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        lineHeight = 28.sp,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onChooseFont,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.add_font))
                }
                if (selectedFontName != null) {
                    Button(
                        onClick = onApply,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.custom_font_apply))
                    }
                }
                if (appliedFontName != null) {
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.reset_font))
                    }
                }
            }
        }
    }
}
