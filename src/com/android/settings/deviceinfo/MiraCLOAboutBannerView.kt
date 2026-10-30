/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo

import android.animation.ValueAnimator
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemProperties
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.Toast
import com.android.settings.R
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * MiraCLO: the banner at the top of Settings > About phone, in the ColorOS layout.
 *
 * A rounded card filled with the user's own wallpaper, "MiraCLO" over it, the line
 * "17.0 | Aurora | Beta 2" and an Update button (a small glass pill with a soft glow) that
 * opens the system update page. The wallpaper settles in when the page opens.
 */
class MiraCLOAboutBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun sp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private var wallpaper: Bitmap? = null
    /** A tiny copy of the wallpaper; drawn stretched under the Update pill it looks blurred. */
    private var blurred: Bitmap? = null

    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shade = Paint()
    // The MiraCLO wordmark: "Mira" in marble white, "CLO" in the brand blue.
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BRAND_WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.DEFAULT, 400, false)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BRAND_WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.DEFAULT, 400, false)
    }
    private val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt() }
    private val pillGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillBlur = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pillTint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val pillRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = 0x59FFFFFF
    }
    private val pillText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, 500, false)
    }

    private val card = RectF()
    private val pill = RectF()
    private val clip = Path()
    private val matrix = Matrix()
    private val pillMatrix = Matrix()

    private val parts: List<String> = versionParts()
    private val updateLabel = context.getString(R.string.miraclo_about_update)

    // 0..1: the wallpaper settling and the text coming in when the page opens.
    private var intro = 0f
    private var pressed = 0f
    private val ease = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val introAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 700
        interpolator = ease
        addUpdateListener { intro = it.animatedValue as Float; invalidate() }
    }
    private var pressAnimator: ValueAnimator? = null

    init {
        contentDescription = NAME + " " + parts.joinToString(" ")
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val cardW = w - dp(SIDE * 2)
        // Same proportions as the ColorOS banner (a little wider than tall).
        val h = (cardW * 356f / 357f + dp(TOP + BOTTOM)).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loadWallpaper()
        intro = 0f
        introAnimator.start()
    }

    override fun onDetachedFromWindow() {
        introAnimator.cancel()
        pressAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun loadWallpaper() {
        val app = context.applicationContext
        EXECUTOR.execute {
            val bmp =
                try {
                    val d: Drawable? =
                        WallpaperManager.getInstance(app).getDrawable(WallpaperManager.FLAG_SYSTEM)
                    (d as? BitmapDrawable)?.bitmap ?: d?.let { drawableToBitmap(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "No wallpaper, using the MiraCLO one", e)
                    null
                }
                    ?: try {
                        BitmapFactory.decodeResource(resources, R.drawable.miraclo_about_wallpaper)
                    } catch (e: RuntimeException) {
                        null
                    }
            val small = bmp?.let {
                try {
                    Bitmap.createScaledBitmap(it, max(1, it.width / 24), max(1, it.height / 24), true)
                } catch (e: RuntimeException) {
                    null
                }
            }
            MAIN.post {
                wallpaper = bmp
                blurred = small
                invalidate()
            }
        }
    }

    private fun drawableToBitmap(d: Drawable): Bitmap? {
        val w = d.intrinsicWidth.takeIf { it > 0 } ?: return null
        val h = d.intrinsicHeight.takeIf { it > 0 } ?: return null
        val scale = minOf(1f, 1440f / w)
        val b = Bitmap.createBitmap((w * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, b.width, b.height)
        d.draw(c)
        return b
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        card.set(dp(SIDE), dp(TOP), w - dp(SIDE), h - dp(BOTTOM))
        clip.reset()
        clip.addRoundRect(card, dp(RADIUS), dp(RADIUS), Path.Direction.CW)
        shade.shader = android.graphics.LinearGradient(0f, card.top, 0f, card.bottom,
            0x1A000000, 0x38000000, Shader.TileMode.CLAMP)
        // Sized to the card (357 dp wide in the ColorOS banner), and never wider than it.
        val s = card.width() / dp(357f)
        namePaint.textSize = dp(46f) * s
        fitWidth(namePaint, NAME, card.width() * 0.62f)
        linePaint.textSize = dp(15f) * s
        fitWidth(linePaint, parts.joinToString("   |   "), card.width() * 0.8f)
        pillText.textSize = sp(16f)
        val pw = pillText.measureText(updateLabel) + dp(52f)
        val ph = dp(44f)
        val cy = card.top + card.height() * 0.81f
        pill.set(card.centerX() - pw / 2f, cy - ph / 2f, card.centerX() + pw / 2f, cy + ph / 2f)
        pillGlow.color = 0x01FFFFFF
    }

    private fun fitWidth(paint: Paint, text: String, max: Float) {
        val w = paint.measureText(text)
        if (w > max) paint.textSize *= max / w
    }

    /** Fills [card] with the wallpaper (centre crop), zoomed by [zoom]. */
    private fun wallMatrix(bmp: Bitmap, out: Matrix, zoom: Float) {
        val scale = max(card.width() / bmp.width, card.height() / bmp.height) * zoom
        val dx = card.centerX() - bmp.width * scale / 2f
        // A little above the middle, where wallpapers usually keep their subject.
        val dy = card.centerY() - bmp.height * scale * 0.45f
        out.setScale(scale, scale)
        out.postTranslate(dx, dy)
    }

    override fun onDraw(canvas: Canvas) {
        val a = intro
        canvas.save()
        canvas.clipPath(clip)
        val bmp = wallpaper
        if (bmp != null) {
            wallMatrix(bmp, matrix, 1.04f - 0.04f * a)
            bmpPaint.alpha = (255 * (0.35f + 0.65f * a)).toInt().coerceIn(0, 255)
            canvas.drawColor(0xFF10131A.toInt())
            canvas.drawBitmap(bmp, matrix, bmpPaint)
        } else {
            canvas.drawColor(0xFF10131A.toInt())
        }
        canvas.drawRect(card, shade)

        // MiraCLO and the version line.
        val rise = dp(10f) * (1f - a)
        val textA = (255 * a).toInt().coerceIn(0, 255)
        namePaint.alpha = textA
        namePaint.setShadowLayer(dp(10f), 0f, dp(1f), 0x40000000)
        val nameBase = card.top + card.height() * 0.46f + rise
        drawName(canvas, nameBase, textA)
        drawVersionLine(canvas, nameBase + linePaint.textSize * 2.1f + rise * 0.5f, textA)

        // Update: a glass pill (the wallpaper blurred under it), a soft glow around it.
        drawPill(canvas, textA)
        canvas.restore()
    }

    private fun drawName(canvas: Canvas, base: Float, alpha: Int) {
        val mira = "Mira"
        val clo = "CLO"
        val x = card.centerX() - namePaint.measureText(NAME) / 2f
        namePaint.color = BRAND_WHITE
        namePaint.alpha = alpha
        canvas.drawText(mira, x, base, namePaint)
        namePaint.color = BRAND_BLUE
        namePaint.alpha = alpha
        canvas.drawText(clo, x + namePaint.measureText(mira), base, namePaint)
    }

    private fun drawVersionLine(canvas: Canvas, base: Float, alpha: Int) {
        if (parts.isEmpty()) return
        val gap = dp(10f)
        val widths = parts.map { linePaint.measureText(it) }
        val total = widths.sum() + gap * 2 * (parts.size - 1)
        var x = card.centerX() - total / 2f
        linePaint.alpha = (0xE6 * alpha / 255)
        linePaint.setShadowLayer(dp(8f), 0f, dp(1f), 0x40000000)
        sepPaint.alpha = (0x99 * alpha / 255)
        val sepTop = base - linePaint.textSize * 0.78f
        val sepBottom = base + linePaint.textSize * 0.12f
        for ((i, part) in parts.withIndex()) {
            canvas.drawText(part, x, base, linePaint)
            x += widths[i]
            if (i < parts.size - 1) {
                x += gap
                canvas.drawRect(x - dp(0.5f), sepTop, x + dp(0.5f), sepBottom, sepPaint)
                x += gap
            }
        }
    }

    private fun drawPill(canvas: Canvas, alpha: Int) {
        val k = 1f - 0.04f * pressed
        canvas.save()
        canvas.scale(k, k, pill.centerX(), pill.centerY())
        val r = pill.height() / 2f
        // Glow: only the shadow shows (the paint itself is almost clear and covered below).
        pillGlow.setShadowLayer(dp(14f), 0f, 0f, ((0x4D * alpha / 255) shl 24) or 0xFFFFFF)
        canvas.drawRoundRect(pill, r, r, pillGlow)
        val small = blurred
        val bmp = wallpaper
        if (small != null && bmp != null) {
            // The same crop as the wallpaper, from the tiny copy: soft and blurred.
            wallMatrix(bmp, pillMatrix, 1.04f - 0.04f * intro)
            pillMatrix.preScale(bmp.width.toFloat() / small.width, bmp.height.toFloat() / small.height)
            val shader = BitmapShader(small, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shader.setLocalMatrix(pillMatrix)
            pillBlur.shader = shader
            pillBlur.alpha = alpha
            canvas.drawRoundRect(pill, r, r, pillBlur)
        }
        pillTint.alpha = (0x33 * alpha / 255 + (0x1A * pressed).toInt()).coerceIn(0, 255)
        canvas.drawRoundRect(pill, r, r, pillTint)
        pillRim.alpha = 0x59 * alpha / 255
        val i = pillRim.strokeWidth / 2f
        canvas.drawRoundRect(pill.left + i, pill.top + i, pill.right - i, pill.bottom - i,
            r - i, r - i, pillRim)
        pillText.alpha = alpha
        canvas.drawText(updateLabel, pill.centerX(),
            pill.centerY() - (pillText.descent() + pillText.ascent()) / 2f, pillText)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val inPill = pill.contains(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!inPill) return false
                animatePress(1f)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!inPill && pressed > 0f) animatePress(0f)
                return true
            }
            MotionEvent.ACTION_UP -> {
                animatePress(0f)
                if (inPill) {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    openUpdates()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                animatePress(0f)
                return true
            }
        }
        return false
    }

    private fun animatePress(to: Float) {
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(pressed, to).apply {
            duration = 160
            addUpdateListener { pressed = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** The OTA page comes in a later build; until then this opens the system update page. */
    private fun openUpdates() {
        val intent = Intent(ACTION_MIRACLO_UPDATES).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent("android.settings.SYSTEM_UPDATE_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else if (fallback.resolveActivity(context.packageManager) != null) {
                context.startActivity(fallback)
            } else {
                Toast.makeText(context, R.string.miraclo_about_no_updates, Toast.LENGTH_SHORT).show()
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.miraclo_about_no_updates, Toast.LENGTH_SHORT).show()
        }
    }

    /** "17.0", "Aurora", "Beta 2" from the build. */
    private fun versionParts(): List<String> {
        val android = Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY.let { if ('.' in it) it else "$it.0" }
        val codename = SystemProperties.get("ro.aospa.miraclo.codename")
        val raw = SystemProperties.get("ro.aospa.miraclo.version")
        val variant = raw.substringAfter('-', "").replace('_', ' ').trim()
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        return listOf(android, codename, variant).filter { it.isNotEmpty() }
    }

    companion object {
        private const val TAG = "MiraCLOAboutBanner"
        private const val NAME = "MiraCLO"
        /** MiraCLO brand colours (wordmark): marble white and CLO blue. */
        private const val BRAND_WHITE = 0xFFEEF3FA.toInt()
        private const val BRAND_BLUE = 0xFF7E99FF.toInt()
        /** Action the MiraCLO updater will answer; until it exists, the system one is used. */
        const val ACTION_MIRACLO_UPDATES = "co.aospa.miraclo.action.CHECK_UPDATES"
        private const val SIDE = 16f
        private const val TOP = 4f
        private const val BOTTOM = 6f
        private const val RADIUS = 30f
        private val EXECUTOR = Executors.newSingleThreadExecutor()
        private val MAIN = Handler(Looper.getMainLooper())
    }
}
