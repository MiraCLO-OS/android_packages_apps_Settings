/*
 * Copyright (C) 2025 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.settings.custom.font

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.FontFamilyUpdateRequest
import android.graphics.fonts.FontFileUpdateRequest
import android.graphics.fonts.FontFileUtil
import android.graphics.fonts.FontManager
import android.graphics.fonts.FontStyle
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.ServiceManager
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import com.android.internal.statusbar.IStatusBarService
import com.android.settings.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel

class ExternalFontInstaller(private val context: Context) {

    companion object {
        private const val TAG = "ExternalFontInstaller"
        private const val CUSTOM_FONT_FILE = "cust_font.ttf"
        private const val TEMP_PREVIEW_FONT = "preview_font.ttf"
        private const val MAX_FONT_BYTES = 32L * 1024L * 1024L
        private const val FONT_PREFERENCES = "custom_font_preferences"
        private const val KEY_INSTALLED_FONT_NAME = "installed_font_name"
        const val DEFAULT_FONT_FAMILY = "sans-serif"

        fun rebootDevice() {
            runCatching {
                val binder = ServiceManager.getService("statusbar")
                val statusBarService = IStatusBarService.Stub.asInterface(binder)
                statusBarService.reboot(false, "system_font_change")
            }.onFailure {
                Log.e(TAG, "Failed to reboot device via statusbar service", it)
            }
        }
    }

    private val fontManager: FontManager = context.getSystemService(FontManager::class.java)

    val installedFontName: String?
        get() = context.getSharedPreferences(FONT_PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_INSTALLED_FONT_NAME, null)

    suspend fun getDisplayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }?.substringBeforeLast('.', missingDelimiterValue = "")
                ?.takeIf { it.isNotBlank() }
        }.onFailure {
            Log.w(TAG, "Failed to read font display name", it)
        }.getOrNull()
    }

    suspend fun loadTypefaceFromUri(uri: Uri): Typeface? = withContext(Dispatchers.IO) {
        val tempFile = copyUriToCache(uri, TEMP_PREVIEW_FONT) ?: return@withContext null
        val postScriptName = extractPostScriptName(tempFile) ?: run {
            tempFile.delete()
            return@withContext null
        }
        return@withContext runCatching {
            Typeface.createFromFile(tempFile)
        }.onFailure {
            Log.e(TAG, "Failed to create preview typeface for $postScriptName", it)
            tempFile.delete()
        }.getOrNull()
    }

    fun loadInstalledTypeface(): Typeface? = runCatching {
        (File(context.filesDir, CUSTOM_FONT_FILE).takeIf { it.isFile }
            ?: File(context.cacheDir, CUSTOM_FONT_FILE).takeIf { it.isFile })
            ?.let { Typeface.createFromFile(it) }
    }.getOrNull()

    suspend fun installFontFromUri(uri: Uri): String? {
        val fontFile = copyUriToCache(uri, CUSTOM_FONT_FILE) ?: run {
            showToast(R.string.toast_failed_apply_font)
            return null
        }

        val postScriptName = extractPostScriptName(fontFile) ?: run {
            showToast(R.string.toast_invalid_font_file)
            fontFile.delete()
            return null
        }

        if (!withContext(Dispatchers.IO) { applyFontToSystem(fontFile, postScriptName) }) {
            fontFile.delete()
            showToast(R.string.toast_failed_apply_font)
            return null
        }

        withContext(Dispatchers.IO) { persistInstalledFont(fontFile) }

        val displayName = getDisplayName(uri)?.takeIf { it.isNotBlank() } ?: postScriptName
        context.getSharedPreferences(FONT_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_INSTALLED_FONT_NAME, displayName)
            .apply()
        cleanupPreviewFont()
        return displayName
    }

    suspend fun resetFontUpdates(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            // FontManager clears all updatable font changes. The UI immediately asks for a
            // reboot so no process keeps using files that the service has removed.
            fontManager.clearUpdates()
            context.getSharedPreferences(FONT_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_INSTALLED_FONT_NAME)
                .apply()
            File(context.filesDir, CUSTOM_FONT_FILE).delete()
            File(context.cacheDir, CUSTOM_FONT_FILE).delete()
            true
        }.onFailure {
            Log.e(TAG, "Failed to reset system font", it)
        }.getOrDefault(false)
    }

    private suspend fun copyUriToCache(uri: Uri, fileName: String): File? =
        withContext(Dispatchers.IO) {
            val cacheFile = File(context.cacheDir, fileName)
            val tempFile = File(context.cacheDir, "$fileName.tmp")
            try {
                tempFile.delete()
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: return@withContext null
                pfd.use { descriptor ->
                    FileInputStream(descriptor.fileDescriptor).use { input ->
                        FileOutputStream(tempFile, false).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var total = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > MAX_FONT_BYTES) {
                                    throw IllegalArgumentException("Font file exceeds size limit")
                                }
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        }
                    }
                }

                if (tempFile.length() <= 0L) {
                    tempFile.delete()
                    return@withContext null
                }

                if (cacheFile.exists() && !cacheFile.delete()) {
                    throw IllegalStateException("Could not replace cached font")
                }
                if (!tempFile.renameTo(cacheFile)) {
                    throw IllegalStateException("Could not install cached font")
                }
                cacheFile
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy URI to cache", e)
                tempFile.delete()
                null
            }
        }

    private fun persistInstalledFont(source: File) {
        val target = File(context.filesDir, CUSTOM_FONT_FILE)
        val temp = File(context.filesDir, "$CUSTOM_FONT_FILE.tmp")
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(temp, false).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            if (!temp.renameTo(target)) {
                if (target.exists() && !target.delete()) {
                    throw IllegalStateException("Could not replace saved font preview")
                }
                if (!temp.renameTo(target)) {
                    throw IllegalStateException("Could not save installed font preview")
                }
            }
        } catch (e: Exception) {
            temp.delete()
            Log.w(TAG, "Could not persist installed font preview", e)
        }
    }

    private fun extractPostScriptName(fontFile: File): String? = runCatching {
        if (!fontFile.isFile || fontFile.length() <= 0L || fontFile.length() > MAX_FONT_BYTES) {
            return@runCatching null
        }
        FileInputStream(fontFile).use { fis ->
            val size = fis.channel.size()
            if (size <= 0L || size > MAX_FONT_BYTES) return@use null
            val buffer = fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, size)
            FontFileUtil.getPostScriptName(buffer, 0)
        }
    }.onFailure {
        Log.e(TAG, "Failed to inspect font file", it)
    }.getOrNull()

    private fun applyFontToSystem(fontFile: File, postScriptName: String): Boolean {
        return runCatching {
            ParcelFileDescriptor.open(fontFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val fontFileUpdateRequest = FontFileUpdateRequest(pfd, ByteArray(0))
                val updateRequestBuilder = FontFamilyUpdateRequest.Builder()
                    .addFontFileUpdateRequest(fontFileUpdateRequest)

                activeFontFamilyNames().forEach { familyName ->
                    val font = FontFamilyUpdateRequest.Font.Builder(
                        postScriptName,
                        FontStyle(),
                    ).build()
                    val family = FontFamilyUpdateRequest.FontFamily.Builder(
                        familyName,
                        listOf(font),
                    ).build()
                    updateRequestBuilder.addFontFamily(family)
                }

                fontManager.updateFontFamily(
                    updateRequestBuilder.build(),
                    fontManager.fontConfig.configVersion,
                ) == FontManager.RESULT_SUCCESS
            }
        }.onFailure {
            Log.e(TAG, "Failed to update system font", it)
        }.getOrDefault(false)
    }

    private fun activeFontFamilyNames(): Set<String> {
        val names = linkedSetOf(DEFAULT_FONT_FAMILY)
        val configFamilyIds = intArrayOf(
            com.android.internal.R.string.config_bodyFontFamily,
            com.android.internal.R.string.config_bodyFontFamilyMedium,
            com.android.internal.R.string.config_headlineFontFamily,
            com.android.internal.R.string.config_headlineFontFamilyMedium,
        )
        configFamilyIds.forEach { resourceId ->
            runCatching { context.resources.getString(resourceId).trim() }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let(names::add)
        }
        return names
    }

    private fun showToast(resId: Int) {
        Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
    }

    fun cleanupPreviewFont() {
        try {
            val previewFile = File(context.cacheDir, TEMP_PREVIEW_FONT)
            if (previewFile.exists()) previewFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup preview font", e)
        }
    }
}
