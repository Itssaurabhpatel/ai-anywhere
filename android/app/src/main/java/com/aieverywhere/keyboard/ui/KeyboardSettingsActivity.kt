package com.aieverywhere.keyboard.ui

import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.aieverywhere.keyboard.R

class KeyboardSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keyboard_settings)

        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.parseColor("#F5F7FA")
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true

        val root = findViewById<View>(R.id.rootKeyboardSettings)
        if (root != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                val sys = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                view.setPadding(0, 0, 0, sys.bottom)
                insets
            }
        }

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.rowAiPreferences).setOnClickListener {
            startActivity(Intent(this, AiSettingsActivity::class.java))
        }

        findViewById<View>(R.id.rowTheme).setOnClickListener {
            startActivity(Intent(this, ThemeActivity::class.java))
        }

        val prefs = getSharedPreferences("AIEverywherePrefs", Context.MODE_PRIVATE)

        findViewById<View>(R.id.rowLanguage).setOnClickListener {
            val languages = arrayOf("English (US)", "English (UK)", "Hindi", "Spanish", "French", "German", "Arabic", "Portuguese", "Russian", "Japanese")
            val enabledLangs = prefs.getStringSet("ENABLED_LANGUAGES", setOf("English (US)", "Hindi")) ?: setOf("English (US)", "Hindi")
            val checkedItems = BooleanArray(languages.size) { i -> languages[i] in enabledLangs }
            
            android.app.AlertDialog.Builder(this)
                .setTitle("Select Languages")
                .setMultiChoiceItems(languages, checkedItems) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }
                .setPositiveButton("OK") { _, _ ->
                    val selected = languages.filterIndexed { i, _ -> checkedItems[i] }.toSet()
                    if (selected.isEmpty()) {
                        Toast.makeText(this, "At least one language must be selected", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    prefs.edit().putStringSet("ENABLED_LANGUAGES", selected).apply()
                    
                    // Ensure current KEYBOARD_LANGUAGE is one of the selected ones
                    val currentLang = prefs.getString("KEYBOARD_LANGUAGE", "English (US)")
                    if (currentLang !in selected) {
                        prefs.edit().putString("KEYBOARD_LANGUAGE", selected.first()).apply()
                    }
                    
                    Toast.makeText(this, "Languages updated", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<View>(R.id.rowLayout).setOnClickListener {
            val layouts = arrayOf("QWERTY", "QWERTZ", "AZERTY", "Colemak", "Dvorak")
            val currentLayout = prefs.getString("KEYBOARD_LAYOUT", "QWERTY")
            val checkedItem = layouts.indexOf(currentLayout).takeIf { it >= 0 } ?: 0
            
            android.app.AlertDialog.Builder(this)
                .setTitle("Select Layout")
                .setSingleChoiceItems(layouts, checkedItem) { dialog, which ->
                    prefs.edit().putString("KEYBOARD_LAYOUT", layouts[which]).apply()
                    Toast.makeText(this, "Layout set to ${layouts[which]}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<View>(R.id.rowPreferences).setOnClickListener {
            val prefsList = arrayOf("Sound on keypress", "Vibrate on keypress", "Popup on keypress")
            val checkedItems = booleanArrayOf(
                prefs.getBoolean("KB_KEY_SOUND", false),
                prefs.getBoolean("HAPTIC_FEEDBACK", true),
                prefs.getBoolean("KB_KEY_POPUP", true)
            )
            
            android.app.AlertDialog.Builder(this)
                .setTitle("Other Preferences")
                .setMultiChoiceItems(prefsList, checkedItems) { _, which, isChecked ->
                    val key = when (which) {
                        0 -> "KB_KEY_SOUND"
                        1 -> "HAPTIC_FEEDBACK"
                        2 -> "KB_KEY_POPUP"
                        else -> ""
                    }
                    if (key.isNotEmpty()) prefs.edit().putBoolean(key, isChecked).apply()
                }
                .setPositiveButton("OK", null)
                .show()
        }

        val switchSuggestions = findViewById<SwitchCompat>(R.id.switchSuggestions)
        styleSwitch(switchSuggestions)
        switchSuggestions.isChecked = prefs.getBoolean("WORD_SUGGESTIONS", true)
        switchSuggestions.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("WORD_SUGGESTIONS", isChecked).apply()
        }

        val switchSummarise = findViewById<SwitchCompat>(R.id.switchSummariseReply)
        styleSwitch(switchSummarise)
        switchSummarise.isChecked = prefs.getBoolean("SUMMARISE_REPLY", true)
        switchSummarise.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("SUMMARISE_REPLY", isChecked).apply()
            try {
                val state = if (isChecked) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                packageManager.setComponentEnabledSetting(ComponentName(this, "com.aieverywhere.keyboard.ui.SummariseActivity"), state, PackageManager.DONT_KILL_APP)
                packageManager.setComponentEnabledSetting(ComponentName(this, "com.aieverywhere.keyboard.ui.ReplyActivity"), state, PackageManager.DONT_KILL_APP)
            } catch (e: Exception) {}
        }

        val switchAutoCorrect = findViewById<SwitchCompat>(R.id.switchAutoCorrection)
        styleSwitch(switchAutoCorrect)
        switchAutoCorrect.isChecked = prefs.getBoolean("AUTO_CORRECTION", true)
        switchAutoCorrect.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("AUTO_CORRECTION", isChecked).apply()
        }

        val switchSwipe = findViewById<SwitchCompat>(R.id.switchSwipeTyping)
        styleSwitch(switchSwipe)
        switchSwipe.isChecked = prefs.getBoolean("SWIPE_TYPING", true)
        switchSwipe.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("SWIPE_TYPING", isChecked).apply()
        }
    }

    private fun styleSwitch(switchView: SwitchCompat) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val thumbColors = intArrayOf(
            android.graphics.Color.parseColor("#7B1FA2"), // Primary Purple when ON
            android.graphics.Color.parseColor("#FAFAFA")  // White when OFF
        )
        val trackColors = intArrayOf(
            android.graphics.Color.parseColor("#D1C4E9"), // Faded Purple when ON
            android.graphics.Color.parseColor("#E0E0E0")  // Grey when OFF
        )
        switchView.thumbTintList = android.content.res.ColorStateList(states, thumbColors)
        switchView.trackTintList = android.content.res.ColorStateList(states, trackColors)
    }
}
