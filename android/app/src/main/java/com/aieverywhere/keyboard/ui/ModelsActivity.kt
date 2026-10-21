package com.aieverywhere.keyboard.ui

import android.content.Intent
import android.os.Bundle
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aieverywhere.keyboard.R
import androidx.appcompat.widget.SwitchCompat

class ModelsActivity : AppCompatActivity() {

    private lateinit var rbGpt4oMini: RadioButton
    private lateinit var rbClaudeSonnet: RadioButton
    private lateinit var rbGeminiFlash: RadioButton
    private lateinit var rbDeepseek: RadioButton
    private lateinit var rbGrok: RadioButton
    private lateinit var rbCloudflare: RadioButton

    
    private lateinit var switchAutoSwitch: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_models)


        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_models

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_models -> true
                R.id.nav_home -> {
                    startActivity(Intent(this, HomeActivity::class.java).apply {
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
                R.id.nav_profile -> {
                    startActivity(Intent(this, SettingsActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }

        val prefs = getSharedPreferences("AIEverywherePrefs", MODE_PRIVATE)
        val selectedModel = prefs.getString("PREFERRED_MODEL", "gemini-3.6-flash")

        rbGpt4oMini = findViewById(R.id.rbGpt4oMini)
        rbClaudeSonnet = findViewById(R.id.rbClaudeSonnet)
        rbGeminiFlash = findViewById(R.id.rbGeminiFlash)
        rbDeepseek = findViewById(R.id.rbDeepseek)
        rbGrok = findViewById(R.id.rbGrok)
        rbCloudflare = findViewById(R.id.rbCloudflare)

        switchAutoSwitch = findViewById(R.id.switchAutoSwitch)

        val allButtons = listOf(rbGpt4oMini, rbClaudeSonnet, rbGeminiFlash, rbDeepseek, rbGrok, rbCloudflare)

        when (selectedModel) {
            "gpt-4o-mini", "gpt-4o" -> rbGpt4oMini.isChecked = true
            "claude-3-5-sonnet" -> rbClaudeSonnet.isChecked = true
            "gemini-3.6-flash" -> rbGeminiFlash.isChecked = true
            "deepseek-chat" -> rbDeepseek.isChecked = true
            "grok" -> rbGrok.isChecked = true
            "cloudflare" -> rbCloudflare.isChecked = true

        }

        val clickListener = android.view.View.OnClickListener { view ->
            for (btn in allButtons) {
                btn.isChecked = false
            }
            (view as RadioButton).isChecked = true

            val newModel = when (view.id) {
                R.id.rbGpt4oMini -> "gpt-4o-mini"
                R.id.rbClaudeSonnet -> "claude-3-5-sonnet"
                R.id.rbGeminiFlash -> "gemini-3.6-flash"
                R.id.rbDeepseek -> "deepseek-chat"
                R.id.rbGrok -> "grok"
                R.id.rbCloudflare -> "cloudflare"

                else -> "gemini-3.6-flash"
            }
            prefs.edit().putString("PREFERRED_MODEL", newModel).apply()
            Toast.makeText(this, "Model updated", Toast.LENGTH_SHORT).show()
        }

        for (btn in allButtons) {
            btn.setOnClickListener(clickListener)
        }
        
        // Redirect buttons
        val redirectListener = android.view.View.OnClickListener {
            startActivity(Intent(this, ApiKeysActivity::class.java))
        }
        

        val btnGoToApiKeys = findViewById<android.widget.Button>(R.id.btnGoToApiKeys)
        btnGoToApiKeys?.setOnClickListener(redirectListener)

        setupSmartFeatures()
    }

    private fun setupSmartFeatures() {
        val prefs = getSharedPreferences("AIEverywherePrefs", MODE_PRIVATE)
        switchAutoSwitch.isChecked = prefs.getBoolean("AUTO_SWITCH", true)
        
        switchAutoSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("AUTO_SWITCH", isChecked).apply()
            val msg = if (isChecked) "Auto-switch enabled" else "Auto-switch disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
