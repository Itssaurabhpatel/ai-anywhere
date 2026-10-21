package com.aieverywhere.keyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View

/** One key, drawn directly on a canvas (label, corner hint, or icon). */
@SuppressLint("ViewConstructor")
class KeyView(context: Context, val def: KeyDef) : View(context) {
    var label: String = def.label
        set(value) { field = value; invalidate() }
    var hint: String? = def.hint
    var icon: Drawable? = null
        set(value) { field = value; invalidate() }
    var textColor: Int = 0xFF000000.toInt()
    var hintColor: Int = 0xFF888888.toInt()
    var textSizePx: Float = 0f
    var iconSizePx: Int = 0
    /** Small caption drawn in the middle of the space bar. */
    var caption: String? = null

    // Touch bookkeeping used by KeyboardView
    internal var pending: Runnable? = null
    internal var longFired = false
    internal var swiping = false
    internal var downX = 0f
    internal var lastX = 0f
    internal var repeatCount = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val ic = icon
        if (ic != null) {
            val s = if (iconSizePx > 0) iconSizePx else (height * 0.45f).toInt()
            ic.setBounds((cx - s / 2f).toInt(), (cy - s / 2f).toInt(), (cx + s / 2f).toInt(), (cy + s / 2f).toInt())
            ic.draw(canvas)
        } else if (label.isNotEmpty()) {
            paint.color = textColor
            paint.textSize = textSizePx
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            val lines = label.split("\n")
            if (lines.size == 1) {
                val maxW = width * 0.86f
                val w = paint.measureText(label)
                if (w > maxW && w > 0) paint.textSize = textSizePx * maxW / w
                canvas.drawText(label, cx, cy - (paint.descent() + paint.ascent()) / 2f, paint)
            } else {
                val maxW = width * 0.86f
                var maxLineW = 0f
                for (l in lines) {
                    val w = paint.measureText(l)
                    if (w > maxLineW) maxLineW = w
                }
                if (maxLineW > maxW && maxLineW > 0) paint.textSize = textSizePx * maxW / maxLineW
                
                val lineHeight = paint.descent() - paint.ascent()
                val totalH = lineHeight * lines.size
                val startY = cy - totalH / 2f - paint.ascent()
                for (i in lines.indices) {
                    canvas.drawText(lines[i], cx, startY + i * lineHeight, paint)
                }
            }
        }
        caption?.let {
            paint.color = hintColor
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            paint.textSize = textSizePx * 0.55f
            canvas.drawText(it, cx, cy - (paint.descent() + paint.ascent()) / 2f, paint)
        }
        val h = hint
        if (!h.isNullOrEmpty() && ic == null) {
            hintPaint.color = hintColor
            hintPaint.textSize = textSizePx * 0.42f
            hintPaint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            val rightPad = com.aieverywhere.keyboard.keyboard.Ui.dpf(context, 3f)
            val topPad = com.aieverywhere.keyboard.keyboard.Ui.dpf(context, 1.5f)
            canvas.drawText(h, width - paddingRight - rightPad, paddingTop + topPad - hintPaint.ascent(), hintPaint)
        }
    }
}


