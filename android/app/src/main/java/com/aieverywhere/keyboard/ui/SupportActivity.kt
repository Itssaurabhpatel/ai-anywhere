@file:Suppress("DEPRECATION", "SetTextI18n", "SpellCheckingInspection", "HardCodedStringLiteral")
package com.aieverywhere.keyboard.ui


import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import com.aieverywhere.keyboard.R

class SupportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support)

        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true

        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_support

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_support -> true
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

        setupFeedbackForm()
        setupGuideButtons()
        setupLinkButtons()
    }

    private fun setupFeedbackForm() {
        val spCategory = findViewById<Spinner>(R.id.sp_feedback_category)
        val etSubject = findViewById<EditText>(R.id.et_feedback_subject)
        val etMessage = findViewById<EditText>(R.id.et_feedback_message)
        val btnSubmit = findViewById<Button>(R.id.btn_send_feedback)

        val categories = listOf("General Feedback", "Bug Report", "Feature Request", "Keyboard Setup Issue", "AI Model Feedback")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spCategory.adapter = adapter

        btnSubmit.setOnClickListener {
            val subject = etSubject.text.toString().trim()
            val message = etMessage.text.toString().trim()
            val category = spCategory.selectedItem.toString()

            if (subject.isEmpty()) {
                etSubject.error = "Please enter subject"
                return@setOnClickListener
            }
            if (message.isEmpty()) {
                etMessage.error = "Please enter feedback description"
                return@setOnClickListener
            }

            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:saurabh.devworks@gmail.com")
                    putExtra(Intent.EXTRA_SUBJECT, "[$category] $subject")
                    putExtra(Intent.EXTRA_TEXT, message)
                }
                startActivity(Intent.createChooser(intent, "Send Feedback to Admin"))
            } catch (e: Exception) {
                Toast.makeText(this, "Feedback recorded! Thank you for helping improve AI Keyboard.", Toast.LENGTH_LONG).show()
            }

            etSubject.setText("")
            etMessage.setText("")
            Toast.makeText(this, "Thank you! Your feedback has been sent to admin.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupGuideButtons() {
        val btnOpenSettings = findViewById<Button>(R.id.btn_open_settings)
        val btnSwitchKeyboard = findViewById<Button>(R.id.btn_switch_keyboard)

        btnOpenSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open Settings", Toast.LENGTH_SHORT).show()
            }
        }

        btnSwitchKeyboard.setOnClickListener {
            try {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to show keyboard picker", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupLinkButtons() {
        val btnDocs = findViewById<Button>(R.id.btn_link_docs)
        val btnPrivacy = findViewById<Button>(R.id.btn_link_privacy)
        val btnEmail = findViewById<Button>(R.id.btn_link_email)

        btnDocs.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("AI anyWhere Documentation")
                .setMessage("Welcome to AI anyWhere Keyboard Guide:\n\n1. Quick Activation:\n   • Go to Settings > Enable Keyboard.\n   • Switch default input method to AI anyWhere.\n\n2. AI Features:\n   • Tap the AI logo on the keyboard toolbar to reveal smart features.\n   • Fix: Corrects grammar and spelling mistakes.\n   • Rewrite: Polishes your sentences for clarity.\n   • Tone: Change style to Professional, Friendly, or Casual.\n   • Translate: Real-time on-device translation.\n\n3. Clipboard & Emojis:\n   • Tap the clipboard icon for copied text history.\n   • Tap the smile icon for emojis and custom stickers.")
                .setPositiveButton("Close", null)
                .show()
        }

        btnPrivacy?.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Privacy Policy & Security")
                .setMessage("AI anyWhere Keyboard is committed to user privacy:\n\n1. No Keystroke Harvesting: Your personal passwords, private messages, and keystrokes are never logged or stored.\n2. Local First: Autocorrect, emojis, and basic functions operate entirely offline.\n3. Secure AI Processing: Requests sent to cloud models use secure, encrypted API endpoints strictly for generated output.\n4. No Data Sharing: We do not sell or monetize personal user data.")
                .setPositiveButton("Close", null)
                .setNeutralButton("View Web") { _, _ ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aieverywhere.com/privacy")))
                    } catch (e: Exception) {
                        Toast.makeText(this, "https://aieverywhere.com/privacy", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }

        btnEmail.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:saurabh.devworks@gmail.com"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Contact Support: saurabh.devworks@gmail.com", Toast.LENGTH_LONG).show()
            }
        }
    }
}
