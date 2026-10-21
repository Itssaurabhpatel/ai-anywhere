package com.aieverywhere.keyboard.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import java.util.Locale

/**
 * Invisible activity the keyboard opens for things an InputMethodService can't do itself:
 * runtime permission prompts (microphone, screenshots) and the system voice dialog fallback.
 * Results go back to AiKeyboardService as a package-local broadcast.
 */
class KeyboardHelperActivity : Activity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_MIC = "mic"
        const val MODE_MEDIA = "media"
        const val MODE_VOICE = "voice"
        const val MODE_IMAGE_UPLOAD = "image_upload"
        const val ACTION_RESULT = "com.aieverywhere.keyboard.HELPER_RESULT"
        const val EXTRA_GRANTED = "granted"
        const val EXTRA_TEXT = "text"
        const val EXTRA_URI = "uri"
        private const val REQ_PERM = 11
        private const val REQ_VOICE = 12
        private const val REQ_IMAGE = 13

        fun launch(ctx: Context, mode: String) {
            ctx.startActivity(
                Intent(ctx, KeyboardHelperActivity::class.java)
                    .putExtra(EXTRA_MODE, mode)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }

        fun mediaPermission(): String =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private var mode = MODE_MIC

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_MIC
        if (savedInstanceState != null) return
        when (mode) {
            MODE_MIC -> ask(Manifest.permission.RECORD_AUDIO)
            MODE_MEDIA -> ask(mediaPermission())
            MODE_VOICE -> startVoiceDialog()
            MODE_IMAGE_UPLOAD -> {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
                startActivityForResult(intent, REQ_IMAGE)
            }
            else -> finish()
        }
    }

    private fun ask(permission: String) {
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            send(true, null); finish(); return
        }
        requestPermissions(arrayOf(permission), REQ_PERM)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (!granted && permissions.isNotEmpty() && !shouldShowRequestPermissionRationale(permissions[0])) {
            // "Don't ask again" was chosen earlier: the only way left is the app's settings page.
            Toast.makeText(this, "Please allow the permission in App info > Permissions", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {}
        }
        send(granted, null)
        finish()
    }

    private fun startVoiceDialog() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_VOICE)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No voice engine found. Install or enable the Google app / Speech Services by Google.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) send(true, text, null)
        } else if (requestCode == REQ_IMAGE && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) send(true, null, uri.toString())
        }
        finish()
    }

    private fun send(granted: Boolean, text: String?, uri: String? = null) {
        val intent = Intent(ACTION_RESULT).setPackage(packageName)
            .putExtra(EXTRA_MODE, mode)
            .putExtra(EXTRA_GRANTED, granted)
            .putExtra(EXTRA_TEXT, text)
        if (uri != null) intent.putExtra(EXTRA_URI, uri)
        sendBroadcast(intent)
    }
}
