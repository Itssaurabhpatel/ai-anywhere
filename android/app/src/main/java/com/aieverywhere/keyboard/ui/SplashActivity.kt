@file:Suppress("DEPRECATION")
package com.aieverywhere.keyboard.ui


import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.RelativeLayout
import com.aieverywhere.keyboard.R
import com.google.firebase.auth.FirebaseAuth

import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            try {
                window.apply {
                    statusBarColor = android.graphics.Color.TRANSPARENT
                    navigationBarColor = android.graphics.Color.TRANSPARENT
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(this, false)
                    val controller = androidx.core.view.WindowCompat.getInsetsController(this, decorView)
                    controller.isAppearanceLightStatusBars = true
                    controller.isAppearanceLightNavigationBars = true
                }
            } catch (e: Exception) { /* ignore */ }

            setContentView(R.layout.activity_splash)

            try {
                // Animated Gradient Background setup
                val splashBg = findViewById<RelativeLayout>(R.id.rl_splash_bg)
                val animDrawable = splashBg?.background as? AnimationDrawable
                animDrawable?.apply {
                    setEnterFadeDuration(600)
                    setExitFadeDuration(600)
                    start()
                }

                // Logo Container Entrance Animation (Scale & Fade)
                val logoContainer = findViewById<View>(R.id.ll_logo_container)
                val ivSplashLogo = findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.iv_splash_logo)
                ivSplashLogo?.setColorFilter(android.graphics.Color.WHITE)
                logoContainer?.apply {
                    alpha = 0f
                    scaleX = 0.85f
                    scaleY = 0.85f
                    animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(700)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            } catch (e: Exception) { /* ignore animation errors */ }

            // 2-second delay then move to Home or Login
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        startActivity(Intent(this, HomeActivity::class.java))
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                } catch (e: Exception) {
                    startActivity(Intent(this, LoginActivity::class.java))
                }
                try {
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                } catch (e: Exception) { /* ignore */ }
                finish()
            }, 2200)
        } catch (fatal: Throwable) {
            // IF ANYTHING FAILS, IMMEDIATELY LAUNCH LOGIN
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
