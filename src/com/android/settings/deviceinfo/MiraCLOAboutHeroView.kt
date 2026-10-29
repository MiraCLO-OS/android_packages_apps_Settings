/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.SystemProperties
import android.provider.Settings
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import com.android.settings.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * MiraCLO: the top of Settings > About phone.
 *
 * The MiraCLO wallpaper (the horizon) fading into the page, the MiraCLO symbol with a soft glow,
 * a thin ring and a small light orbiting it, the wordmark, the release ("1.0 Beta 2 · Aurora")
 * and four quiet tiles: device, Android, kernel and build.
 *
 * Opening the page plays a short entrance: the wallpaper settles from a slight zoom, the symbol
 * rises, the ring appears, then the wordmark, the release and the tiles follow one by one.
 * Everything is drawn here, so it costs one view.
 */
class MiraCLOAboutHeroView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val wallpaper: Bitmap? = decode(R.drawable.miraclo_about_wallpaper)
    private val symbol: Bitmap? = decode(R.drawable.miraclo_about_symbol)
    private val wordmark: Bitmap? = decode(R.drawable.miraclo_about_wordmark)

    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fadePaint = Paint()
    private val basePaint = Paint()
    private var wallBottom = 0f
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = 0x1A72C4FF
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD9BD.toInt() }
    private val dotGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val versionPaint = text(14f, 400, 0xFFC9D3EA.toInt(), Paint.Align.CENTER)
    private val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6B7590.toInt() }
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB80C101A.toInt() }
    private val tileRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = 0x0DFFFFFF
    }
    private val keyPaint = text(12f, 400, 0xFF8B93A6.toInt(), Paint.Align.LEFT)
    private val valuePaint = text(16f, 400, 0xFFEEF2F8.toInt(), Paint.Align.LEFT)
    private val subPaint = text(12f, 400, 0xFF8B93A6.toInt(), Paint.Align.LEFT)

    private val matrix = Matrix()
    private val rect = RectF()
    private val pageColor = resolvePageColor()

    private val release = releaseLine()
    private val codename = SystemProperties.get("ro.aospa.miraclo.codename")
    private val tiles = buildTiles()

    // Layout (dp)
    private val ringTop = dp(36f)
    private val ringRadius = dp(132f)
    private val symbolW = dp(150f)
    private val wordmarkW = dp(176f)
    private val tileH = dp(78f)
    private val gap = dp(10f)
    private val side = dp(16f)
    private val tilesTop = dp(422f)

    // Animation
    private var intro = 0f // 0..INTRO_MS, in ms
    private var spin = 0f // 0..1, one orbit
    private val ease = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)

    private val introAnimator =
        ValueAnimator.ofFloat(0f, INTRO_MS).apply {
            duration = INTRO_MS.toLong()
            interpolator = LinearInterpolator()
            addUpdateListener {
                intro = it.animatedValue as Float
                invalidate()
            }
        }
    private val idleAnimator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ORBIT_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                spin = it.animatedValue as Float
                invalidate()
            }
        }

    init {
        contentDescription = listOf("MiraCLO", release, codename).filter { it.isNotEmpty() }.joinToString(", ")
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (tilesTop + tileH * 2 + gap + dp(30f)).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Gradients are made once per size; the animation only changes their alpha.
        // The hero is the night sky in both themes: the wallpaper fades into it, and only
        // the last strip below the tiles fades into the page.
        wallBottom = wallpaper?.let { w.toFloat() / it.width * it.height } ?: 0f
        fadePaint.shader = LinearGradient(0f, wallBottom - dp(120f), 0f, wallBottom,
            SKY and 0x00FFFFFF, SKY, Shader.TileMode.CLAMP)
        basePaint.shader = LinearGradient(0f, h - dp(30f), 0f, h.toFloat(),
            SKY, pageColor, Shader.TileMode.CLAMP)
        glowPaint.shader = RadialGradient(w / 2f, ringTop + ringRadius, dp(150f),
            intArrayOf(0x335A96FF, 0x0D5A96FF, 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        dotGlow.shader = RadialGradient(0f, 0f, dp(8f), 0x59FFBF8F, 0, Shader.TileMode.CLAMP)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // The entrance plays each time the page opens.
        intro = 0f
        introAnimator.start()
        idleAnimator.start()
    }

    override fun onDetachedFromWindow() {
        introAnimator.cancel()
        idleAnimator.cancel()
        super.onDetachedFromWindow()
    }

    /** Progress 0..1 of an element that starts at [delayMs] and takes [durMs]. */
    private fun step(delayMs: Float, durMs: Float): Float =
        ease.getInterpolation(((intro - delayMs) / durMs).coerceIn(0f, 1f))

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f

        canvas.drawRect(0f, 0f, w, h, basePaint)

        // Wallpaper: covers the width, settles from a slight zoom, fades into the sky.
        val wp = step(0f, 1300f)
        wallpaper?.let { bmp ->
            val scale = w / bmp.width * (1.08f - 0.08f * wp)
            matrix.setScale(scale, scale)
            matrix.postTranslate(cx - bmp.width * scale / 2f, 0f)
            bmpPaint.alpha = (255 * wp).toInt()
            canvas.save()
            canvas.clipRect(0f, 0f, w, wallBottom)
            canvas.drawBitmap(bmp, matrix, bmpPaint)
            canvas.restore()
            canvas.drawRect(0f, wallBottom - dp(120f), w, wallBottom, fadePaint)
        }

        val cy = ringTop + ringRadius

        // Soft glow behind the symbol, breathing slowly.
        val breathe = 0.5f + 0.5f * sin(spin * 2.0 * Math.PI * 3).toFloat()
        val glowA = step(400f, 900f) * (0.6f + 0.4f * breathe)
        glowPaint.alpha = (255 * glowA).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, dp(150f), glowPaint)

        // Ring and the light orbiting on it.
        val ring = step(350f, 1000f)
        if (ring > 0f) {
            val r = ringRadius * (0.6f + 0.4f * ring)
            ringPaint.alpha = (0x1A * ring).toInt()
            canvas.drawCircle(cx, cy, r, ringPaint)
            val angle = (spin * 2.0 * Math.PI - Math.PI / 2).toFloat()
            val dx = cx + r * cos(angle)
            val dy = cy + r * sin(angle)
            dotGlow.alpha = (255 * ring).toInt()
            canvas.save()
            canvas.translate(dx, dy)
            canvas.drawCircle(0f, 0f, dp(8f), dotGlow)
            canvas.restore()
            dotPaint.alpha = (255 * ring).toInt()
            canvas.drawCircle(dx, dy, dp(2.5f), dotPaint)
        }

        // Symbol: rises in and grows to size.
        val sym = step(200f, 1100f)
        symbol?.let { bmp ->
            val sw = symbolW * (0.86f + 0.14f * sym)
            val sh = sw * bmp.height / bmp.width
            val top = cy - sh / 2f + dp(18f) * (1f - sym)
            rect.set(cx - sw / 2f, top, cx + sw / 2f, top + sh)
            bmpPaint.alpha = (255 * sym).toInt()
            canvas.drawBitmap(bmp, null, rect, bmpPaint)
        }

        // Wordmark and release line.
        var y = ringTop + ringRadius * 2 + dp(26f)
        val wm = step(550f, 800f)
        wordmark?.let { bmp ->
            val ww = wordmarkW
            val wh = ww * bmp.height / bmp.width
            val top = y + dp(16f) * (1f - wm)
            rect.set(cx - ww / 2f, top, cx + ww / 2f, top + wh)
            bmpPaint.alpha = (255 * wm).toInt()
            canvas.drawBitmap(bmp, null, rect, bmpPaint)
            y += wh
        }
        val rl = step(700f, 800f)
        if (release.isNotEmpty() || codename.isNotEmpty()) {
            val base = y + dp(28f) + dp(16f) * (1f - rl)
            versionPaint.alpha = (255 * rl).toInt()
            if (release.isNotEmpty() && codename.isNotEmpty()) {
                val a = versionPaint.measureText(release)
                val b = versionPaint.measureText(codename)
                val gapX = dp(16f)
                val start = cx - (a + gapX + b) / 2f
                versionPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(release, start, base, versionPaint)
                sepPaint.alpha = (255 * rl).toInt()
                canvas.drawCircle(start + a + gapX / 2f, base - dp(4.5f), dp(1.5f), sepPaint)
                canvas.drawText(codename, start + a + gapX, base, versionPaint)
                versionPaint.textAlign = Paint.Align.CENTER
            } else {
                canvas.drawText(release.ifEmpty { codename }, cx, base, versionPaint)
            }
        }

        // Tiles, one after another.
        val tileW = (w - side * 2 - gap) / 2f
        tiles.forEachIndexed { i, t ->
            val p = step(850f + i * 70f, 700f)
            if (p <= 0f) return@forEachIndexed
            val col = i % 2
            val row = i / 2
            val left = side + col * (tileW + gap)
            val top = tilesTop + row * (tileH + gap) + dp(16f) * (1f - p)
            val a = (255 * p).toInt()
            rect.set(left, top, left + tileW, top + tileH)
            tilePaint.alpha = (0xB8 * p).toInt()
            canvas.drawRoundRect(rect, dp(22f), dp(22f), tilePaint)
            tileRim.alpha = (0x0D * p).toInt()
            canvas.drawRoundRect(rect, dp(22f), dp(22f), tileRim)
            val tx = left + dp(16f)
            keyPaint.alpha = a
            valuePaint.alpha = a
            subPaint.alpha = a
            canvas.drawText(t.key, tx, top + dp(24f), keyPaint)
            canvas.drawText(ellipsize(t.value, valuePaint, tileW - dp(28f)), tx, top + dp(46f), valuePaint)
            if (t.sub.isNotEmpty()) {
                canvas.drawText(ellipsize(t.sub, subPaint, tileW - dp(28f)), tx, top + dp(64f), subPaint)
            }
        }
    }

    // Data ------------------------------------------------------------------------------------

    private data class Tile(val key: String, val value: String, val sub: String)

    private fun buildTiles(): List<Tile> {
        val res = context.resources
        val deviceName =
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                ?.takeIf { it.isNotBlank() } ?: Build.MODEL
        val android = Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY
        val patch = formatPatch(Build.VERSION.SECURITY_PATCH)
        val kernel = (System.getProperty("os.version") ?: "").substringBefore('-')
        val variant = buildVariant()
        val date = SystemProperties.getLong("ro.build.date.utc", 0L).takeIf { it > 0 }?.let {
            SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(it * 1000))
        } ?: ""
        return listOf(
            Tile(res.getString(R.string.miraclo_about_device), deviceName, Build.DEVICE),
            Tile(res.getString(R.string.miraclo_about_android), android,
                if (patch.isEmpty()) "" else res.getString(R.string.miraclo_about_patch, patch)),
            Tile(res.getString(R.string.miraclo_about_kernel), kernel, ""),
            Tile(res.getString(R.string.miraclo_about_build), variant, date),
        )
    }

    /** "1.0-beta_2" -> "1.0 Beta 2"; "1.0-unofficial" -> "1.0 Unofficial". */
    private fun releaseLine(): String {
        val raw = SystemProperties.get("ro.aospa.miraclo.version")
        if (raw.isEmpty()) return ""
        val number = raw.substringBefore('-')
        val variant = raw.substringAfter('-', "").replace('_', ' ').trim()
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        return if (variant.isEmpty()) number else "$number $variant"
    }

    private fun buildVariant(): String {
        val raw = SystemProperties.get("ro.aospa.miraclo.version").substringAfter('-', "")
        val word = raw.substringBefore('_').ifEmpty { "unofficial" }
        return word.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    private fun formatPatch(patch: String?): String {
        if (patch.isNullOrEmpty()) return ""
        return try {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(patch) ?: return patch
            SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(d)
        } catch (e: java.text.ParseException) {
            patch
        }
    }

    // Helpers ---------------------------------------------------------------------------------

    private fun resolvePageColor(): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(android.R.attr.colorBackground, tv, true) &&
            tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT
        ) {
            tv.data
        } else {
            0xFF000000.toInt()
        }
    }

    private fun decode(id: Int): Bitmap? =
        try {
            BitmapFactory.decodeResource(resources, id)
        } catch (e: RuntimeException) {
            null
        }

    private fun ellipsize(s: String, p: Paint, max: Float): String {
        if (p.measureText(s) <= max) return s
        var end = s.length
        while (end > 1 && p.measureText(s, 0, end) + p.measureText("…") > max) end--
        return s.substring(0, end) + "…"
    }

    private fun text(sizeSp: Float, weight: Int, color: Int, align: Paint.Align) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, resources.displayMetrics)
            typeface = Typeface.create(Typeface.DEFAULT, weight, false)
            this.color = color
            textAlign = align
        }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    companion object {
        private const val INTRO_MS = 1600f
        private const val ORBIT_MS = 16000L
        private const val SKY = 0xFF04070C.toInt()
    }
}
