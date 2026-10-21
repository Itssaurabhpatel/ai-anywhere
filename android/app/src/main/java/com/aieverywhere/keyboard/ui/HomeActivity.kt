@file:Suppress("DEPRECATION", "SetTextI18n", "SpellCheckingInspection", "HardCodedStringLiteral")
package com.aieverywhere.keyboard.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.Toast
import android.app.Activity
import android.content.Intent
import android.widget.TextView
import com.aieverywhere.keyboard.R
import com.google.firebase.auth.FirebaseAuth
import android.view.WindowManager
import android.view.View
import android.widget.ViewFlipper

import androidx.appcompat.app.AppCompatActivity

class HomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Transparent status bar — background shows through
        window.apply {
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(this, false)
            val controller = androidx.core.view.WindowCompat.getInsetsController(this, decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()
        
        val user = auth.currentUser
        val tvGreetingName = findViewById<TextView>(R.id.tvGreetingName)
        val tvAiUserName = findViewById<TextView>(R.id.tvAiUserName)
        val llAppBranding = findViewById<android.widget.LinearLayout>(R.id.llAppBranding)
        val ivBrandLogo = findViewById<ImageView>(R.id.ivBrandLogo)
        val tvBrandName = findViewById<TextView>(R.id.tvBrandName)

        // No tint - show logo in original black color

        // Set greeting with real user name, reload for fresh data
        if (user != null) {
            val initialName = user.displayName
                ?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "User"
            tvGreetingName?.text = "Hi, $initialName 👋"

            user.reload().addOnCompleteListener {
                val fresh = auth.currentUser
                val name = fresh?.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: fresh?.email?.substringBefore("@")
                    ?: "User"
                tvGreetingName?.text = "Hi, $name 👋"
                // tvAiUserName?.text = name
            }
        }

        // Entrance animation: slide up + fade in for branding line
        llAppBranding?.apply {
            alpha = 0f
            translationY = 30f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(700)
                .setStartDelay(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }


        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
        
        bottomNav.selectedItemId = R.id.nav_home

        
        // --- AI Auto Rotation Logic ---
        val ivAiProviderLogo = findViewById<ImageView>(R.id.ivAiProviderLogo)
        val tvAiModelName = findViewById<TextView>(R.id.tvAiModelName)
        val layoutAiContent = findViewById<android.widget.LinearLayout>(R.id.layoutAiContent)

        val aiModels = listOf(
            Triple(R.drawable.ic_openai, "OpenAI", "GPT-4o | Use For Advanced Tasks"),
            Triple(R.drawable.ic_gemini, "Gemini", "Gemini 1.5 Flash | Fast & Smart"),
            Triple(R.drawable.ic_anthropic, "Anthropic", "Claude 3.5 Sonnet | Best For Coding"),
            Triple(R.drawable.ic_deepseek, "DeepSeek", "DeepSeek Coder | Best For Development"),
            Triple(R.drawable.ic_groq, "Groq", "Llama 3 | Lightning Fast"),
            Triple(R.drawable.ic_cloudflare, "Cloudflare", "Workers AI | Edge Inference")
        )

        var currentAiIndex = 0
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                currentAiIndex = (currentAiIndex + 1) % aiModels.size
                val nextModel = aiModels[currentAiIndex]

                // Fade out
                layoutAiContent.animate().alpha(0f).setDuration(300).withEndAction {
                    // Update content
                    ivAiProviderLogo.setImageResource(nextModel.first)
                    tvAiUserName.text = nextModel.second
                    tvAiModelName.text = nextModel.third
                    
                    // Fade in
                    layoutAiContent.animate().alpha(1f).setDuration(300).start()
                }.start()

                handler.postDelayed(this, 3000)
            }
        }
        handler.postDelayed(runnable, 3000)
        // ------------------------------
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
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

        setupCoreFeatureTiles()
        setupPrivacyCard()
    }

    private fun setupCoreFeatureTiles() {
        findViewById<View>(R.id.tileGrammar)?.setOnClickListener {
            startActivity(Intent(this, AiSettingsActivity::class.java))
        }

        findViewById<View>(R.id.tileTone)?.setOnClickListener {
            startActivity(Intent(this, AiSettingsActivity::class.java))
        }

        findViewById<View>(R.id.tileTranslate)?.setOnClickListener {
            startActivity(Intent(this, KeyboardSettingsActivity::class.java))
        }

        findViewById<View>(R.id.tileAskAi)?.setOnClickListener {
            startActivity(Intent(this, AiSettingsActivity::class.java))
        }
    }

    private fun setupPrivacyCard() {
        findViewById<View>(R.id.cardPrivacy)?.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Privacy First Guarantee")
                .setMessage("AI anyWhere Keyboard is built to protect your private data:\n\n1. Zero Keystroke Logging: We never record, harvest, or store typed words, credentials, or private chats.\n2. Local Offline Tools: Basic typing, autocorrect, and local models run 100% offline.\n3. Encrypted AI Requests: Selected AI prompts are securely sent to your chosen AI provider without saving copies on our servers.\n4. No Data Reselling: Your data belongs strictly to you.")
                .setPositiveButton("Close", null)
                .show()
        }
    }
}
