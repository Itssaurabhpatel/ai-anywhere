@file:Suppress("DEPRECATION", "SetTextI18n", "SpellCheckingInspection", "HardCodedStringLiteral")

package com.aieverywhere.keyboard.ui





import androidx.appcompat.app.AppCompatActivity
import android.app.AlertDialog
import android.content.Context
import androidx.core.content.edit
import android.content.Intent
import android.net.Uri
import android.os.Bundle

import android.provider.Settings

import android.view.View

import android.view.inputmethod.InputMethodManager

import android.widget.Button

import androidx.appcompat.widget.SwitchCompat

import android.widget.TextView

import android.widget.Toast

import com.aieverywhere.keyboard.R

import com.google.firebase.auth.FirebaseAuth



class SettingsActivity : AppCompatActivity() {



    private lateinit var tvEmail: TextView

    private lateinit var tvProfileName: TextView

    private lateinit var btnUpgrade: Button

    private lateinit var tvSelectedModelName: TextView

    private lateinit var tvSavedKeysSummary: TextView

    private lateinit var switchHaptic: androidx.appcompat.widget.SwitchCompat

    private lateinit var switchAutoSwitch: androidx.appcompat.widget.SwitchCompat



    private lateinit var auth: FirebaseAuth



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)



        window.statusBarColor = android.graphics.Color.WHITE

        window.navigationBarColor = android.graphics.Color.WHITE

        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)

        controller.isAppearanceLightStatusBars = true

        controller.isAppearanceLightNavigationBars = true



        auth = FirebaseAuth.getInstance()



        // Views Initialization

        tvEmail = findViewById(R.id.tvEmail)

        tvProfileName = findViewById(R.id.tvProfileName)

        btnUpgrade = findViewById(R.id.btnUpgrade)

        tvSelectedModelName = findViewById(R.id.tvSelectedModelName)

        tvSavedKeysSummary = findViewById(R.id.tvSavedKeysSummary)

        switchHaptic = findViewById(R.id.switchHaptic)

        switchAutoSwitch = findViewById(R.id.switchAutoSwitch)



        val btnEnableKeyboard = findViewById<Button>(R.id.btnEnableKeyboard)

        val btnSwitchKeyboard = findViewById<Button>(R.id.btnSwitchKeyboard)

        val btnManageModels = findViewById<Button>(R.id.btnManageModels)

        val btnManageKeys = findViewById<Button>(R.id.btnManageKeys)

        val btnLogout = findViewById<View>(R.id.btnLogoutText)



        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)

        bottomNav.selectedItemId = R.id.nav_profile



        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_profile -> true
                R.id.nav_home -> {
                    startActivity(Intent(this, HomeActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_models -> {
                    startActivity(Intent(this, ModelsActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_create -> {
                    startActivity(Intent(this, ApiKeysActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_support -> {
                    startActivity(Intent(this, SupportActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }





        setupAccountUI()

        setupKeyboardControls(btnEnableKeyboard, btnSwitchKeyboard)

        setupPreferences()

        setupShortcuts(btnManageModels, btnManageKeys)

        findViewById<Button>(R.id.btnKeyboardSettings)?.setOnClickListener {
            startActivity(Intent(this, KeyboardSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnAiSettings)?.setOnClickListener {
            startActivity(Intent(this, AiSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnKeyboardTheme)?.setOnClickListener {

            startActivity(Intent(this, ThemeActivity::class.java))

        }

        findViewById<Button>(R.id.btnMyStickers)?.setOnClickListener {

            startActivity(Intent(this, StickerManagerActivity::class.java))

        }



        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.tvPrivacyPolicy)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Privacy Policy & Security")
                .setMessage("AI anyWhere Keyboard is built privacy-first:\n\n1. Zero Keystroke Logging: We never log, save, or harvest your keystrokes or passwords.\n2. User-Initiated AI Only: AI features run only when you explicitly tap an action button.\n3. Encryption & Safety: Any processed snippets use direct, encrypted connections to selected AI APIs.\n4. Offline Support: Offline translation and basic typing require no network access.")
                .setPositiveButton("Close", null)
                .setNeutralButton("Open Website") { _, _ ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aieverywhere.com/privacy")))
                    } catch (e: Exception) {
                        Toast.makeText(this, "https://aieverywhere.com/privacy", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }
    }



    override fun onResume() {

        super.onResume()

        updateDynamicSummaries()

    }



    private fun setupAccountUI() {
        val user = auth.currentUser
        if (user != null) {
            val initialName = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "User"
            tvProfileName.text = initialName
            tvEmail.text = user.email ?: ""

            // Background reload to ensure freshest account state
            user.reload().addOnCompleteListener {
                val freshUser = auth.currentUser
                if (freshUser != null) {
                    val freshName = freshUser.displayName?.takeIf { it.isNotBlank() }
                        ?: freshUser.email?.substringBefore("@")
                        ?: "User"
                    tvProfileName.text = freshName
                    tvEmail.text = freshUser.email ?: ""
                }
            }
        } else {
            tvEmail.text = "Guest User"
            tvProfileName.text = "Guest"
        }



        btnUpgrade.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Your Plan: Full Access")
                .setMessage("You have full access to all AI Keyboard features:\n\n• Advanced AI Grammar, Tone, and Rewrite\n• Multi-Language Translation (Offline & Online)\n• Custom Themes, Emojis, and Stickers\n• Safe & Private On-Device Processing")
                .setPositiveButton("OK", null)
                .show()
        }

    }



    private fun setupKeyboardControls(btnEnable: Button, btnSwitch: Button) {

        btnEnable.setOnClickListener {

            try {

                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))

            } catch (e: Exception) {

                Toast.makeText(this, "Unable to open Keyboard Settings", Toast.LENGTH_SHORT).show()

            }

        }



        btnSwitch.setOnClickListener {

            try {

                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

                imm.showInputMethodPicker()

            } catch (e: Exception) {

                Toast.makeText(this, "Unable to open Input Method Picker", Toast.LENGTH_SHORT).show()

            }

        }

    }



    private fun setupPreferences() {

        val prefs = getSharedPreferences("AIEverywherePrefs", Context.MODE_PRIVATE)



        switchHaptic.isChecked = prefs.getBoolean("HAPTIC_FEEDBACK", true)

        switchHaptic.setOnCheckedChangeListener { _, isChecked ->

            prefs.edit().putBoolean("HAPTIC_FEEDBACK", isChecked).apply()

        }



        switchAutoSwitch.isChecked = prefs.getBoolean("AUTO_SWITCH", true)

        switchAutoSwitch.setOnCheckedChangeListener { _, isChecked ->

            prefs.edit().putBoolean("AUTO_SWITCH", isChecked).apply()

        }

    }



    private fun setupShortcuts(btnModels: Button, btnKeys: Button) {

        btnModels.setOnClickListener {

            startActivity(Intent(this, ModelsActivity::class.java))

            overridePendingTransition(0, 0)

        }



        btnKeys.setOnClickListener {

            startActivity(Intent(this, ApiKeysActivity::class.java))

            overridePendingTransition(0, 0)

        }

    }



    private fun updateDynamicSummaries() {

        val prefs = getSharedPreferences("AIEverywherePrefs", android.content.Context.MODE_PRIVATE)



        val modelCode = prefs.getString("PREFERRED_MODEL", "gemini-3.6-flash") ?: "gemini-3.6-flash"

        val modelDisplay = when (modelCode) {

            "gemini-3.6-flash" -> "Google Gemini"

            "gpt-4o-mini", "gpt-4o" -> "Open AI"

            "claude-3-5-sonnet" -> "Anthropic"

            "deepseek-chat" -> "DeepSeek V4 Flash"

            "grok" -> "Groq (GPT-OSS 120B)"

            "cloudflare" -> "Cloudflare AI (Free)"



            else -> "Google Gemini"

        }

        tvSelectedModelName.text = modelDisplay



        val savedKeysList = mutableListOf<String>()

        if (!prefs.getString("OPENAI_KEY", "").isNullOrEmpty()) savedKeysList.add("Open AI")

        if (!prefs.getString("GEMINI_KEY", "").isNullOrEmpty()) savedKeysList.add("Gemini")

        if (!prefs.getString("DEEPSEEK_KEY", "").isNullOrEmpty()) savedKeysList.add("DeepSeek")

        if (!prefs.getString("ANTHROPIC_KEY", "").isNullOrEmpty()) savedKeysList.add("Anthropic")

        if (!prefs.getString("GROK_KEY", "").isNullOrEmpty()) savedKeysList.add("Groq")

        if (!prefs.getString("CLOUDFLARE_KEY", "").isNullOrEmpty()) savedKeysList.add("Cloudflare")





        if (savedKeysList.isNotEmpty()) {

            tvSavedKeysSummary.text = "Saved: " + savedKeysList.joinToString(", ")

        } else {

            tvSavedKeysSummary.text = "No keys configured yet (Tap Configure to add)"

        }

    }

}



