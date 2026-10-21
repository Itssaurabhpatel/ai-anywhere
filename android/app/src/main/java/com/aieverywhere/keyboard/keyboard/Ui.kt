package com.aieverywhere.keyboard.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView

/** Small drawing helpers shared by the keyboard panels. */
object Ui {
    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
    fun dpf(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

    fun rounded(color: Int, radiusPx: Float, strokeColor: Int = Color.TRANSPARENT, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(color)
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    /** Key background with a pressed state; the pressed flag is set manually by the touch handler. */
    fun keyBackground(ctx: Context, theme: KeyboardTheme, color: Int, radiusDp: Float = 7f): Drawable {
        val r = dpf(ctx, radiusDp)
        val stroke = if (theme.keyBorders) dp(ctx, 1f) else 0
        val strokeColor = if (theme.isDark) 0x40FFFFFF else 0x30000000
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(theme.pressed, r, strokeColor, stroke))
            addState(intArrayOf(), rounded(color, r, strokeColor, stroke))
        }
    }

    fun chipBackground(ctx: Context, theme: KeyboardTheme): Drawable {
        val r = dpf(ctx, 18f)
        val stroke = dp(ctx, 1f)
        val strokeColor = if (theme.isDark) 0x40FFFFFF else 0x30000000
        val bg = if (theme.isDark) theme.keyBg else theme.specialKeyBg
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(theme.pressed, r, strokeColor, stroke))
            addState(intArrayOf(), rounded(bg, r, strokeColor, stroke))
        }
    }

    fun tint(v: ImageView, color: Int) {
        v.imageTintList = ColorStateList.valueOf(color)
    }

    /**
     * Recolours an inflated XML panel (translate / text edit / AI preview) so it follows the theme.
     * Views whose ids are in [skipIds] are left untouched (e.g. the app logo).
     */
    fun applyTheme(view: View, theme: KeyboardTheme, skipIds: Set<Int> = emptySet(), buttonBg: ((View) -> Drawable?)? = null) {
        if (view.id != View.NO_ID && view.id in skipIds) return
        when (view) {
            is EditText -> {
                view.setTextColor(theme.keyText)
                view.setHintTextColor(theme.hintText)
            }
            is Button -> {
                view.setTextColor(theme.keyText)
                buttonBg?.invoke(view)?.let { view.background = it }
            }
            is TextView -> {
                view.setTextColor(theme.keyText)
                view.setHintTextColor(theme.hintText)
                if (view.isClickable) {
                    buttonBg?.invoke(view)?.let { view.background = it }
                }
            }
            is ImageButton, is ImageView -> tint(view as ImageView, theme.icon)
            is ProgressBar -> view.indeterminateTintList = ColorStateList.valueOf(theme.accent)
        }
        if (view is ViewGroup && view !is Spinner) {
            for (i in 0 until view.childCount) applyTheme(view.getChildAt(i), theme, skipIds, buttonBg)
        }
    }

    /** Decodes a (possibly large) image scaled down so neither side exceeds [maxSide]. */
    fun decodeScaled(path: String, maxSide: Int): Bitmap? {
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            var sample = 1
            while (o.outWidth / (sample * 2) >= maxSide || o.outHeight / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Throwable) {
            null
        }
    }

    /** Background photo that always fills the keyboard (centre-cropped at draw time, never stretched). */
    fun photoDrawable(path: String, dimPercent: Int, focusPercent: Int, maxSide: Int = 1440): Drawable? {
        val bmp = decodeScaled(path, maxSide) ?: return null
        return PhotoDrawable(bmp, dimPercent, focusPercent)
    }
}

class PhotoDrawable(private val bmp: Bitmap, dimPercent: Int, focusPercent: Int) : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dim = Color.argb((dimPercent.coerceIn(0, 90) * 255) / 100, 0, 0, 0)
    private val focus = focusPercent.coerceIn(0, 100) / 100f
    private val src = Rect()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val scale = maxOf(b.width().toFloat() / bmp.width, b.height().toFloat() / bmp.height)
        val sw = (b.width() / scale).toInt().coerceAtMost(bmp.width)
        val sh = (b.height() / scale).toInt().coerceAtMost(bmp.height)
        val sx = (bmp.width - sw) / 2
        val sy = ((bmp.height - sh) * focus).toInt()
        src.set(sx, sy, sx + sw, sy + sh)
        canvas.drawBitmap(bmp, src, b, paint)
        canvas.drawColor(dim)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
}
