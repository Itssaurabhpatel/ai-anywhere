@file:Suppress("DEPRECATION", "SetTextI18n")

package com.aieverywhere.keyboard.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.aieverywhere.keyboard.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

open class ProcessTextActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
        val isReadOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)

        if (selectedText.isBlank()) {
            Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val isReply = this is ReplyActivity || intent.component?.className?.contains("Reply") == true
        val actionType = if (isReply) "REPLY" else "SUMMARIZE"
        val titleText = if (isReply) "AI Smart Reply" else "AI Summary"

        // Root Dimmed Overlay Container
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setBackgroundColor(Color.parseColor("#80000000"))
            setOnClickListener { finish() }
        }

        // BottomSheet Card Layout
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
            }
            setPadding(dp(20), dp(18), dp(20), dp(24))
            setOnClickListener { /* prevent dismiss when clicking card */ }
        }

        // Header Title
        val tvHeader = TextView(this).apply {
            text = titleText
            textSize = 18f
            setTextColor(Color.parseColor("#7B1FA2"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        card.addView(tvHeader)

        // Loading indicator & Result Text Container
        val tvStatus = TextView(this).apply {
            text = "Thinking…"
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, dp(10), 0, dp(10))
        }
        val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }
        card.addView(tvStatus)
        card.addView(pb, LinearLayout.LayoutParams(-1, dp(4)).apply { setMargins(0, dp(4), 0, dp(12)) })

        val tvResult = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#1C1C1E"))
            visibility = View.GONE
        }
        val scrollResult = ScrollView(this).apply {
            addView(tvResult)
        }
        card.addView(scrollResult, LinearLayout.LayoutParams(-1, dp(140)).apply { setMargins(0, dp(4), 0, dp(16)) })

        // Action Buttons Row
        val llButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            visibility = View.GONE
        }

        fun btn(label: String, isPrimary: Boolean, onClick: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            if (isPrimary) {
                setTextColor(Color.WHITE)
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.parseColor("#AB47BC"), Color.parseColor("#7B1FA2"))
                ).apply { cornerRadius = dp(10).toFloat() }
            } else {
                setTextColor(Color.parseColor("#7B1FA2"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F3E5F5"))
                    cornerRadius = dp(10).toFloat()
                }
            }
            isClickable = true
            setOnClickListener { onClick() }
        }

        val btnCopy = btn("Copy", false) {
            val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clip.setPrimaryClip(android.content.ClipData.newPlainText(titleText, tvResult.text.toString()))
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            finish()
        }

        val btnInsert = btn(if (!isReadOnly) "Replace Text" else "Insert", true) {
            val resultText = tvResult.text.toString()
            if (!isReadOnly) {
                val resIntent = Intent().apply {
                    putExtra(Intent.EXTRA_PROCESS_TEXT, resultText)
                }
                setResult(RESULT_OK, resIntent)
            } else {
                val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText(titleText, resultText))
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            finish()
        }

        llButtons.addView(btnCopy, LinearLayout.LayoutParams(-2, dp(40)).apply { marginEnd = dp(10) })
        llButtons.addView(btnInsert, LinearLayout.LayoutParams(-2, dp(40)))
        card.addView(llButtons)

        root.addView(card, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        // Execute AI Action
        scope.launch {
            val res = ApiClient.executeAction(this@ProcessTextActivity, actionType, selectedText)
            pb.visibility = View.GONE
            tvStatus.visibility = View.GONE
            res.onSuccess { response ->
                tvResult.text = response.resultText
                tvResult.visibility = View.VISIBLE
                llButtons.visibility = View.VISIBLE
            }.onFailure { err ->
                tvResult.text = err.message ?: "Failed to generate AI response."
                tvResult.setTextColor(Color.RED)
                tvResult.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}

class SummariseActivity : ProcessTextActivity()
class ReplyActivity : ProcessTextActivity()
