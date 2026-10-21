package com.aieverywhere.keyboard.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.*
import android.widget.FrameLayout
import com.aieverywhere.keyboard.R
import kotlin.math.abs

class FloatingBubbleView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onBubbleClicked: () -> Unit
) {
    private var bubbleView: View = LayoutInflater.from(context).inflate(R.layout.floating_bubble_layout, null)
    private var params: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    init {
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        setupTouchListener()
        windowManager.addView(bubbleView, params)
    }

    private fun setupTouchListener() {
        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (diffX < 10 && diffY < 10) {
                        onBubbleClicked()
                    } else {
                        snapToEdge()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val middle = screenWidth / 2

        params.x = if (params.x < middle) 0 else screenWidth - bubbleView.width
        windowManager.updateViewLayout(bubbleView, params)
    }

    fun remove() {
        try {
            windowManager.removeView(bubbleView)
        } catch (e: Exception) {}
    }
}
