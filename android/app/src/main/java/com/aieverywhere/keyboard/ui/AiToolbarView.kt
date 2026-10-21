package com.aieverywhere.keyboard.ui

import android.view.View
import android.widget.Button
import android.widget.ImageButton
import com.aieverywhere.keyboard.R

interface AiToolbarListener {
    fun onAiActionTriggered(actionType: String, tone: String? = null)
    fun onEmojiToggle()
    fun onClipboardToggle()
    fun onVoiceTypingToggle()
    fun onTranslateToggle()
    fun onGifToggle()
    fun onImageUploadToggle()
    fun onToolbarCollapsed()
}

class AiToolbarView(private val rootView: View, private val listener: AiToolbarListener) {
    init {
        val svFeatureIcons = rootView.findViewById<View>(R.id.sv_feature_icons)
        val svAiActions = rootView.findViewById<View>(R.id.sv_ai_actions)
        val btnToolbarToggle = rootView.findViewById<ImageButton>(R.id.btn_toolbar_toggle)

        // Toggle: click logo -> show AI buttons, switch icon to Cut/Cross sign (ic_clear)
        // Click again -> hide AI buttons, switch icon back to App Logo (app_logo)
        btnToolbarToggle?.setOnClickListener {
            rootView.findViewById<View>(R.id.tv_voice_status)?.visibility = View.GONE
            rootView.findViewById<View>(R.id.personal_info_suggestions_zone)?.visibility = View.GONE
            val scale = rootView.resources.displayMetrics.density
            if (svAiActions?.visibility == View.VISIBLE) {
                // Hide AI actions, show feature icons
                svAiActions.visibility = View.GONE
                svFeatureIcons?.visibility = View.VISIBLE
                btnToolbarToggle.setImageResource(R.drawable.app_logo)
                val pad = (6 * scale + 0.5f).toInt()
                btnToolbarToggle.setPadding(pad, pad, pad, pad)
                listener.onToolbarCollapsed()
            } else {
                // Show AI actions, hide feature icons
                svAiActions?.visibility = View.VISIBLE
                svFeatureIcons?.visibility = View.GONE
                btnToolbarToggle.setImageResource(R.drawable.ic_cross_black)
                val pad = (6 * scale + 0.5f).toInt()
                btnToolbarToggle.setPadding(pad, pad, pad, pad)
            }
        }

        // Feature Icon Buttons
        rootView.findViewById<ImageButton>(R.id.btn_action_emoji)?.setOnClickListener {
            listener.onEmojiToggle()
        }

        rootView.findViewById<ImageButton>(R.id.btn_action_clipboard)?.setOnClickListener {
            listener.onClipboardToggle()
        }

        rootView.findViewById<ImageButton>(R.id.btn_action_mic)?.setOnClickListener {
            listener.onVoiceTypingToggle()
        }

        rootView.findViewById<ImageButton>(R.id.btn_action_translate_icon)?.setOnClickListener {
            listener.onTranslateToggle()
        }

        rootView.findViewById<ImageButton>(R.id.btn_action_gif)?.setOnClickListener {
            listener.onGifToggle()
        }



        // Press feedback (scale + haptic) on every toolbar button
        listOf(R.id.btn_toolbar_toggle, R.id.btn_action_emoji, R.id.btn_action_clipboard, R.id.btn_action_gif,
            R.id.btn_action_translate_icon, R.id.btn_action_settings, R.id.btn_action_mic, R.id.btn_action_fix,
            R.id.btn_action_rewrite, R.id.btn_action_reply, R.id.btn_action_tone, R.id.btn_action_custom
        ).forEach { id -> rootView.findViewById<View>(id)?.let { attachPressFx(it) } }

        // AI Action Buttons
        rootView.findViewById<View>(R.id.btn_action_fix)?.setOnClickListener {
            listener.onAiActionTriggered("FIX")
        }

        rootView.findViewById<View>(R.id.btn_action_rewrite)?.setOnClickListener {
            listener.onAiActionTriggered("REWRITE")
        }

        rootView.findViewById<View>(R.id.btn_action_reply)?.setOnClickListener {
            listener.onAiActionTriggered("REPLY")
        }

        rootView.findViewById<View>(R.id.btn_action_tone)?.setOnClickListener {
            listener.onAiActionTriggered("TONE", null)
        }

        rootView.findViewById<View>(R.id.btn_action_custom)?.setOnClickListener {
            listener.onAiActionTriggered("CUSTOM_PROMPT")
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun attachPressFx(v: View) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    view.animate().cancel()
                    view.animate().scaleX(0.88f).scaleY(0.88f).alpha(0.75f).setDuration(70).start()
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start()
            }
            false // let the normal click handler still fire
        }
    }
}
