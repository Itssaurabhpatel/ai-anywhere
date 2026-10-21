package com.aieverywhere.keyboard.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.TextView
import com.aieverywhere.keyboard.R

class FloatingAssistantService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatingBubble: FloatingBubbleView? = null
    private var expandedView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    private fun showBubble() {
        floatingBubble = FloatingBubbleView(this, windowManager) {
            toggleExpandedState()
        }
    }

    private fun toggleExpandedState() {
        if (expandedView == null) {
            expandedView = LayoutInflater.from(this).inflate(R.layout.floating_expanded_card, null)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            expandedView?.findViewById<TextView>(R.id.btn_close_expanded)?.setOnClickListener {
                dismissExpandedState()
            }

            windowManager.addView(expandedView, params)
        } else {
            dismissExpandedState()
        }
    }

    private fun dismissExpandedState() {
        expandedView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
            expandedView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingBubble?.remove()
        dismissExpandedState()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
