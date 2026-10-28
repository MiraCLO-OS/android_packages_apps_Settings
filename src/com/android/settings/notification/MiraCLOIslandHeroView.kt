/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.notification

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/**
 * MiraCLO: the picture at the top of Settings > Notifications > Dynamic Island.
 *
 * The top of a phone screen with the island on it: a call pill (green dot and time) on the camera
 * and a round music pill beside it. Drawn rather than an image so it follows light and dark theme.
 */
class MiraCLOIslandHeroView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val card = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strip = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.5f)
        color = 0x24FFFFFF
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF22C55E.toInt() }
    private val time = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4ADE80.toInt()
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.DEFAULT, 600, false)
        textSize = sp(11f)
        fontFeatureSettings = "tnum"
    }
    private val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val stripPath = Path()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun night(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    override fun onDraw(canvas: Canvas) {
        val dark = night()
        card.color = context.getColor(
            if (dark) android.R.color.system_surface_container_high_dark
            else android.R.color.system_surface_container_high_light
        )
        strip.color = context.getColor(
            if (dark) android.R.color.system_surface_container_highest_dark
            else android.R.color.system_surface_container_highest_light
        )

        // Card.
        val r = dp(28f)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, card)

        // The top of a screen: 240 x 70, rounded top corners, centred.
        val sw = dp(240f)
        val sh = dp(70f)
        val sl = (width - sw) / 2f
        val st = dp(22f)
        val sr = dp(22f)
        stripPath.reset()
        stripPath.addRoundRect(
            sl, st, sl + sw, st + sh,
            floatArrayOf(sr, sr, sr, sr, 0f, 0f, 0f, 0f),
            Path.Direction.CW,
        )
        canvas.save()
        canvas.clipPath(stripPath)
        canvas.drawRect(sl, st, sl + sw, st + sh, strip)

        // Main pill: call, green dot and time.
        val pl = sl + dp(38f)
        val pt = st + dp(12f)
        val pw = dp(126f)
        val ph = dp(26f)
        pill.shader = LinearGradient(0f, pt, 0f, pt + ph, 0xFF16171C.toInt(), 0xFF000000.toInt(), Shader.TileMode.CLAMP)
        rect.set(pl, pt, pl + pw, pt + ph)
        canvas.drawRoundRect(rect, ph / 2f, ph / 2f, pill)
        rect.inset(rim.strokeWidth / 2f, rim.strokeWidth / 2f)
        canvas.drawRoundRect(rect, ph / 2f, ph / 2f, rim)
        val d = dp(18f)
        canvas.drawCircle(pl + dp(4f) + d / 2f, pt + ph / 2f, d / 2f, dot)
        val baseline = pt + ph / 2f - (time.descent() + time.ascent()) / 2f
        canvas.drawText("02:14", pl + pw - dp(10f), baseline, time)

        // Round pill: music, album art.
        val bl = sl + dp(170f)
        val bs = dp(26f)
        canvas.drawCircle(bl + bs / 2f, pt + bs / 2f, bs / 2f, bubble)
        val a = dp(16f)
        val al = bl + (bs - a) / 2f
        val at = pt + (bs - a) / 2f
        art.shader = LinearGradient(
            al, at, al + a, at + a,
            intArrayOf(0xFFF59E0B.toInt(), 0xFFEF4444.toInt(), 0xFF7C3AED.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        rect.set(al, at, al + a, at + a)
        canvas.drawRoundRect(rect, dp(5f), dp(5f), art)
        canvas.restore()
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
