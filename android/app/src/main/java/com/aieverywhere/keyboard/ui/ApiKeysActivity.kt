@file:Suppress("DEPRECATION", "SetTextI18n", "SpellCheckingInspection", "HardCodedStringLiteral")

package com.aieverywhere.keyboard.ui





import androidx.appcompat.app.AppCompatActivity

import android.content.Intent

import android.os.Bundle

import android.widget.Button

import android.widget.EditText

import android.widget.ImageView

import android.widget.Toast

import com.aieverywhere.keyboard.R



import android.app.AlertDialog

import android.widget.LinearLayout

import android.widget.TextView



class ApiKeysActivity : AppCompatActivity() {



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_api_keys)



        window.statusBarColor = android.graphics.Color.WHITE

        window.navigationBarColor = android.graphics.Color.WHITE

        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)

        controller.isAppearanceLightStatusBars = true

        controller.isAppearanceLightNavigationBars = true



        val prefs = getSharedPreferences("AIEverywherePrefs", MODE_PRIVATE)



        val etContextData = findViewById<EditText>(R.id.etContextData)
        val btnSaveContextData = findViewById<Button>(R.id.btnSaveContextData)
        
        etContextData.setText(prefs.getString("PERSONAL_CONTEXT_DATA", ""))
        
        // Allow EditText to scroll vertically inside the parent ScrollView
        etContextData.setOnTouchListener { v, event ->
            if (v.hasFocus()) {
                v.parent.requestDisallowInterceptTouchEvent(true)
                if (event.action and android.view.MotionEvent.ACTION_MASK == android.view.MotionEvent.ACTION_UP) {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
        
        btnSaveContextData.setOnClickListener {
            val contextText = etContextData.text.toString().trim()
            prefs.edit().putString("PERSONAL_CONTEXT_DATA", contextText).apply()
            Toast.makeText(this, "Personal context saved successfully!", Toast.LENGTH_SHORT).show()
        }

        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)

        bottomNav.selectedItemId = R.id.nav_create



        bottomNav.setOnItemSelectedListener { item ->

            when (item.itemId) {

                R.id.nav_create -> true

                R.id.nav_home -> {

                    val intent = Intent(this, HomeActivity::class.java)

                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

                    startActivity(intent)

                    overridePendingTransition(0, 0)

                    finish()

                    true

                }

                R.id.nav_models -> {

                    startActivity(Intent(this, ModelsActivity::class.java))

                    overridePendingTransition(0, 0)

                    finish()

                    true

                }

                R.id.nav_support -> {

                    startActivity(Intent(this, SupportActivity::class.java))

                    overridePendingTransition(0, 0)

                    finish()

                    true

                }

                R.id.nav_profile -> {

                    startActivity(Intent(this, SettingsActivity::class.java))

                    overridePendingTransition(0, 0)

                    finish()

                    true

                }

                else -> false

            }

        }



        val providers = listOf(

            Triple(R.id.rowOpenAi, "OPENAI_KEY", "OpenAI"),

            Triple(R.id.rowAnthropic, "ANTHROPIC_KEY", "Anthropic"),

            Triple(R.id.rowGemini, "GEMINI_KEY", "Google Gemini"),

            Triple(R.id.rowDeepseek, "DEEPSEEK_KEY", "DeepSeek"),

            Triple(R.id.rowCloudflare, "CLOUDFLARE_KEY", "Cloudflare"),

            Triple(R.id.rowGroq, "GROK_KEY", "Groq")
        )



        for ((rowId, prefKey, providerName) in providers) {

            val row = findViewById<LinearLayout>(rowId)

            val btnText = row.getChildAt(4) as TextView

            

            // Check if key exists to update button text visually

            val existingKey = prefs.getString(prefKey, "")
            if (!existingKey.isNullOrEmpty()) {
                btnText.text = "Update"
                btnText.setBackgroundResource(R.drawable.bg_pill_gradient_purple)
                btnText.setTextColor(android.graphics.Color.WHITE)
            } else {
                btnText.text = "Add Key"
                btnText.setBackgroundResource(R.drawable.bg_pill_border_gradient)
                btnText.setTextColor(android.graphics.Color.parseColor("#1A1A1A"))
            }



            val clickListener = android.view.View.OnClickListener {

                val dialogView = layoutInflater.inflate(R.layout.dialog_api_key, null)

                val dialog = AlertDialog.Builder(this)

                    .setView(dialogView)

                    .create()



                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)



                val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)

                val etKey = dialogView.findViewById<EditText>(R.id.etDialogKey)

                val ivToggleVisibility = dialogView.findViewById<ImageView>(R.id.ivToggleVisibility)

                val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)

                val btnSave = dialogView.findViewById<Button>(R.id.btnDialogSave)



                tvTitle.text = android.text.Html.fromHtml("Add <i>$providerName</i> Key", android.text.Html.FROM_HTML_MODE_LEGACY)

                etKey.setText(prefs.getString(prefKey, ""))

                etKey.hint = when (prefKey) {

                    "CLOUDFLARE_KEY" -> "ACCOUNT_ID:API_TOKEN"

                    "GEMINI_KEY" -> "AIza..."

                    "GROK_KEY" -> "gsk_... (Groq) or xai-... (Grok)"



                    "ANTHROPIC_KEY" -> "sk-ant-..."

                    else -> "sk-..."

                }



                var isPasswordVisible = false

                ivToggleVisibility.setOnClickListener {

                    isPasswordVisible = !isPasswordVisible

                    if (isPasswordVisible) {

                        etKey.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

                        ivToggleVisibility.setImageResource(R.drawable.ic_visibility)

                    } else {

                        etKey.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

                        ivToggleVisibility.setImageResource(R.drawable.ic_visibility_off)

                    }

                    etKey.setSelection(etKey.text.length)

                }



                btnCancel.setOnClickListener {

                    dialog.dismiss()

                }



                btnSave.setOnClickListener {

                    val newKey = etKey.text.toString().trim()

                    prefs.edit().putString(prefKey, newKey).apply()

                    Toast.makeText(this, "Key saved!", Toast.LENGTH_SHORT).show()

                    

                    if (newKey.isNotEmpty()) {
                        btnText.text = "Update"
                        btnText.setBackgroundResource(R.drawable.bg_pill_gradient_purple)
                        btnText.setTextColor(android.graphics.Color.WHITE)
                    } else {
                        btnText.text = "Add Key"
                        btnText.setBackgroundResource(R.drawable.bg_pill_border_gradient)
                        btnText.setTextColor(android.graphics.Color.parseColor("#1A1A1A"))
                    }

                    dialog.dismiss()

                }



                dialog.show()

            }

            

            row.setOnClickListener(clickListener)

            btnText.setOnClickListener(clickListener)

        }



        findViewById<android.widget.TextView>(R.id.linkOpenAi)?.setOnClickListener {

            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://platform.openai.com/api-keys")))

        }

        findViewById<android.widget.TextView>(R.id.linkAnthropic)?.setOnClickListener {

            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://console.anthropic.com/settings/keys")))

        }

        findViewById<android.widget.TextView>(R.id.linkGemini)?.setOnClickListener {

            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/app/apikey")))

        }

        findViewById<android.widget.TextView>(R.id.linkDeepseek)?.setOnClickListener {

            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://platform.deepseek.com/api_keys")))

        }

        findViewById<android.widget.TextView>(R.id.linkCloudflare)?.setOnClickListener {

            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://dash.cloudflare.com/profile/api-tokens")))

        }

        findViewById<android.widget.TextView>(R.id.linkGroq)?.setOnClickListener {

            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://console.groq.com/keys")))

        }



    }



    override fun onBackPressed() {

        super.onBackPressed()

        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)

    }

}



