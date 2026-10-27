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
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemProperties
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import com.android.settings.R
import kotlin.math.max

/**
 * MiraCLO: banner at the top of Settings > About phone.
 *
 * The ROM name and release ("MiraCLO", "1.0 | Aurora") over the MiraCLO wallpaper, with a glass
 * rim, a gloss, a slow shine sweeping across, a soft coloured halo underneath, and a glow that
 * follows the finger (the image also shifts slightly with it, and the banner presses in).
 */
class MiraCLOBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val radius = dp(30f)
    private val image: Bitmap? = BitmapFactory.decodeResource(resources, R.drawable.miraclo_about_banner)

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlay = Paint(Paint.ANTI_ALIAS_FLAG)
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.DEFAULT, 600, false)
        textSize = sp(56f)
        letterSpacing = -0.025f
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, 500, false)
        textSize = sp(17f)
        letterSpacing = 0.02f
        color = 0xF0FFFFFF.toInt()
        setShadowLayer(dp(8f), 0f, dp(1f), 0x59000000)
    }

    private val rect = RectF()
    private val clip = Path()
    private val imageMatrix = Matrix()

    private val subtitle: String = buildSubtitle()

    private var touchX = 0f
    private var touchY = 0f
    private var touching = false
    private var glowAlpha = 0f
    private var shiftX = 0f
    private var shiftY = 0f
    private var sheen = 0f

    private var glowAnimator: ValueAnimator? = null
    private val sheenAnimator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                sheen = it.animatedValue as Float
                invalidate()
            }
        }
    private val press = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)

    init {
        contentDescription = "MiraCLO $subtitle"
        isClickable = true
    }

    private fun buildSubtitle(): String {
        val version = SystemProperties.get("ro.aospa.miraclo.version").substringBefore('-')
        val codename = SystemProperties.get("ro.aospa.miraclo.codename")
        return when {
            version.isEmpty() -> codename
            codename.isEmpty() -> version
            else -> "$version | $codename"
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sheenAnimator.start()
    }

    override fun onDetachedFromWindow() {
        sheenAnimator.cancel()
        glowAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Soft coloured halo under the banner (drawn outside our bounds; the parent does not clip).
        halo.shader = RadialGradient(w * 0.3f, h * 0.85f, w * 0.55f, 0x806E8CFF.toInt(), 0, Shader.TileMode.CLAMP)
        canvas.drawCircle(w * 0.3f, h * 0.85f, w * 0.55f, halo)
        halo.shader = RadialGradient(w * 0.78f, h * 0.7f, w * 0.45f, 0x66FF965A, 0, Shader.TileMode.CLAMP)
        canvas.drawCircle(w * 0.78f, h * 0.7f, w * 0.45f, halo)

        canvas.save()
        clip.reset()
        rect.set(0f, 0f, w, h)
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(clip)

        // Wallpaper, centre-cropped, slightly enlarged so it can shift with the finger.
        canvas.drawColor(0xFF070B18.toInt())
        image?.let { bmp ->
            val scale = max(w / bmp.width, h / bmp.height) * 1.06f
            imageMatrix.setScale(scale, scale)
            imageMatrix.postTranslate((w - bmp.width * scale) / 2f + shiftX, (h - bmp.height * scale) / 2f + shiftY)
            canvas.drawBitmap(bmp, imageMatrix, imagePaint)
        }

        // Darken towards the bottom, then a gloss from the top-left.
        overlay.shader = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(0x0D050814, 0x4D050814, 0x9E050814.toInt()), floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP)
        canvas.drawRect(rect, overlay)
        overlay.shader = LinearGradient(0f, 0f, w * 0.6f, h * 0.9f, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawRect(rect, overlay)

        // Shine sweeping across now and then.
        val bandX = -w * 0.3f + sheen * w * 2.2f
        if (bandX < w * 1.3f) {
            overlay.shader = LinearGradient(bandX, 0f, bandX + dp(70f), h * 0.35f,
                intArrayOf(0x00FFFFFF, 0x2EFFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            canvas.drawRect(rect, overlay)
        }

        // Glow under the finger.
        if (glowAlpha > 0.01f) {
            // Subtle: a faint light under the finger, never a flash.
            val r = w * 0.38f
            val a = glowAlpha
            glow.shader = RadialGradient(touchX, touchY, r,
                intArrayOf(Color.argb((38 * a).toInt(), 255, 255, 255),
                    Color.argb((28 * a).toInt(), 160, 185, 255),
                    Color.argb((12 * a).toInt(), 255, 160, 110), Color.TRANSPARENT),
                floatArrayOf(0f, 0.28f, 0.6f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(touchX, touchY, r, glow)
        }

        // "MiraCLO" with a soft blue glow, and the release below it.
        val mira = "Mira"
        val clo = "CLO"
        val total = titlePaint.measureText(mira) + titlePaint.measureText(clo)
        val baseline = h / 2f + titlePaint.textSize * 0.25f
        var x = (w - total) / 2f
        titlePaint.setShadowLayer(dp(14f), 0f, 0f, 0x8C9BB8FF.toInt())
        titlePaint.color = Color.WHITE
        canvas.drawText(mira, x, baseline, titlePaint)
        x += titlePaint.measureText(mira)
        titlePaint.color = 0xFFA9C1FF.toInt()
        canvas.drawText(clo, x, baseline, titlePaint)
        if (subtitle.isNotEmpty()) {
            canvas.drawText(subtitle, w / 2f, baseline + dp(34f), subPaint)
        }

        canvas.restore()

        // Glass rim
        rim.strokeWidth = dp(1f)
        rim.shader = LinearGradient(0f, 0f, 0f, h, 0x73FFFFFF, 0x1FFFFFFF, Shader.TileMode.CLAMP)
        rect.set(rim.strokeWidth / 2f, rim.strokeWidth / 2f, w - rim.strokeWidth / 2f, h - rim.strokeWidth / 2f)
        canvas.drawRoundRect(rect, radius, radius, rim)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                touching = true
                track(event)
                animateGlow(1f)
                animate().scaleX(0.985f).scaleY(0.985f).setDuration(220).setInterpolator(press).start()
            }
            MotionEvent.ACTION_MOVE -> track(event)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                touching = false
                animateGlow(0f)
                animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(press).start()
            }
        }
        invalidate()
        return true
    }

    private fun track(event: MotionEvent) {
        touchX = event.x
        touchY = event.y
        val w = max(width, 1).toFloat()
        val h = max(height, 1).toFloat()
        shiftX = -((event.x / w) - 0.5f) * 2f * dp(6f)
        shiftY = -((event.y / h) - 0.5f) * 2f * dp(6f)
    }

    private fun animateGlow(target: Float) {
        glowAnimator?.cancel()
        val startShiftX = shiftX
        val startShiftY = shiftY
        glowAnimator =
            ValueAnimator.ofFloat(glowAlpha, target).apply {
                duration = if (target > glowAlpha) 160 else 480
                addUpdateListener {
                    glowAlpha = it.animatedValue as Float
                    if (!touching) {
                        // Ease the image back to the centre as the glow fades.
                        val f = if (target == 0f && startShiftX != 0f) glowAlpha else 1f
                        shiftX = startShiftX * f
                        shiftY = startShiftY * f
                    }
                    invalidate()
                }
                start()
            }
    }

    private fun dp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
