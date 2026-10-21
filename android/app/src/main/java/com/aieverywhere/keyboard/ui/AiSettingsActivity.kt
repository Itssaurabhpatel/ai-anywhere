@file:Suppress("DEPRECATION", "SetTextI18n", "HardCodedStringLiteral")

package com.aieverywhere.keyboard.ui

import androidx.appcompat.app.AppCompatActivity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.aieverywhere.keyboard.R

/**
 * AI Preferences screen styled with primary Purple gradient theme matching the main app.
 */
class AiSettingsActivity : AppCompatActivity() {

    companion object {
        const val PREF_TONE = "AI_DEFAULT_TONE"
        const val PREF_REPLY_QUESTION = "AI_REPLY_WITH_QUESTION"
        const val PREF_USE_CONTEXT = "AI_USE_PERSONAL_CONTEXT"
        const val PREF_CONTEXT_DATA = "PERSONAL_CONTEXT_DATA"
        const val TONE_AUTO = "Auto"
        val TONES = listOf(TONE_AUTO, "Professional", "Friendly", "Casual", "Formal", "Funny", "Confident", "Empathetic", "Concise")
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.statusBarColor = Color.parseColor("#FFFFFF")
        window.navigationBarColor = Color.parseColor("#F5F7FA")
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val prefs = getSharedPreferences("AIEverywherePrefs", Context.MODE_PRIVATE)

        // Main Container
        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F7FA"))
        }

        // Top Header Bar
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), 0, dp(16), 0)
            elevation = dp(2).toFloat()
        }
        val btnBack = ImageView(this).apply {
            setImageResource(R.drawable.ic_back_modern)
            setPadding(0, dp(4), dp(12), dp(4))
            isClickable = true
            setOnClickListener { finish() }
        }
        val tvTitle = TextView(this).apply {
            text = "AI Preferences"
            textSize = 20f
            setTextColor(Color.parseColor("#1C1C1E"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        headerBar.addView(btnBack, LinearLayout.LayoutParams(dp(36), dp(36)))
        headerBar.addView(tvTitle, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        mainContainer.addView(headerBar, LinearLayout.LayoutParams(-1, dp(56)))

        // Content ScrollView
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }

        // Card Container for Options
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(16).toFloat()
            }
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }

        // --- 1. Tone Row ---
        val tvTone = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#7B1FA2"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        fun refreshTone() { tvTone.text = prefs.getString(PREF_TONE, TONE_AUTO) ?: TONE_AUTO }
        refreshTone()

        card.addView(row("Default tone", "Used by Tone and Reply. \"Auto\" matches the message.", tvTone) {
            val cur = TONES.indexOf(prefs.getString(PREF_TONE, TONE_AUTO)).coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle("Select Default Tone")
                .setSingleChoiceItems(TONES.toTypedArray(), cur) { d, which ->
                    prefs.edit().putString(PREF_TONE, TONES[which]).apply()
                    refreshTone()
                    d.dismiss()
                }.show()
        })

        card.addView(divider())

        // --- 2. Reply & Ask AI with Question Row ---
        card.addView(switchRow(
            "Reply & Ask AI with a question",
            "AI ends replies & Ask AI responses with a short follow-up question",
            prefs.getBoolean(PREF_REPLY_QUESTION, false)
        ) { prefs.edit().putBoolean(PREF_REPLY_QUESTION, it).apply() })

        card.addView(divider())

        // --- 3. Personal Context Row ---
        val etContext = EditText(this).apply {
            hint = "e.g. My name is Rahul. I am a software engineer. My tone is polite and professional."
            setHintTextColor(Color.parseColor("#A0A0A5"))
            setText(prefs.getString(PREF_CONTEXT_DATA, ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 4
            gravity = Gravity.TOP
            textSize = 14f
            setTextColor(Color.parseColor("#1C1C1E"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F0F3F8"))
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(14), dp(14), dp(14), dp(14))
            visibility = if (prefs.getBoolean(PREF_USE_CONTEXT, true)) View.VISIBLE else View.GONE
        }

        card.addView(switchRow(
            "Use personal context",
            "Let AI use your saved info to personalise responses",
            prefs.getBoolean(PREF_USE_CONTEXT, true)
        ) {
            prefs.edit().putBoolean(PREF_USE_CONTEXT, it).apply()
            etContext.visibility = if (it) View.VISIBLE else View.GONE
        })

        card.addView(etContext, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        contentLayout.addView(card)

        // --- Save Button with App's Primary Purple Gradient ---
        val btnSave = TextView(this).apply {
            text = "Save Preferences"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#AB47BC"),
                    Color.parseColor("#7B1FA2"),
                    Color.parseColor("#4A148C")
                )
            ).apply {
                cornerRadius = dp(14).toFloat()
            }
            elevation = dp(3).toFloat()
            isClickable = true
            setOnClickListener {
                prefs.edit().putString(PREF_CONTEXT_DATA, etContext.text.toString().trim()).apply()
                Toast.makeText(this@AiSettingsActivity, "AI Preferences Saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        contentLayout.addView(btnSave, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(24) })

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(contentLayout)
        }
        mainContainer.addView(scrollView, LinearLayout.LayoutParams(-1, -1))

        setContentView(mainContainer)
    }

    private fun titleSub(title: String, sub: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = title; textSize = 15f
            setTextColor(Color.parseColor("#1C1C1E"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addView(TextView(context).apply {
            text = sub; textSize = 12f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, dp(2), 0, 0)
        })
    }

    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply {
            setMargins(0, dp(8), 0, dp(8))
        }
        setBackgroundColor(Color.parseColor("#EAEAEA"))
    }

    private fun row(title: String, sub: String, trailing: View, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(12), 0, dp(12))
        isClickable = true
        setBackgroundResource(android.R.drawable.list_selector_background)
        setOnClickListener { onClick() }
        addView(titleSub(title, sub), LinearLayout.LayoutParams(0, -2, 1f))
        addView(trailing)
    }

    private fun switchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(12), 0, dp(12))
        val sw = SwitchCompat(context).apply {
            isChecked = checked
            styleSwitch(this)
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }
        setOnClickListener { sw.toggle() }
        addView(titleSub(title, sub), LinearLayout.LayoutParams(0, -2, 1f))
        addView(sw)
    }

    private fun styleSwitch(switchView: SwitchCompat) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val thumbColors = intArrayOf(
            Color.parseColor("#7B1FA2"), // App Primary Purple when ON
            Color.parseColor("#FAFAFA")  // White when OFF
        )
        val trackColors = intArrayOf(
            Color.parseColor("#D1C4E9"), // Faded Purple Track when ON
            Color.parseColor("#E0E0E0")  // Grey when OFF
        )
        switchView.thumbTintList = android.content.res.ColorStateList(states, thumbColors)
        switchView.trackTintList = android.content.res.ColorStateList(states, trackColors)
    }
}
