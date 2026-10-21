package com.aieverywhere.keyboard.keyboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/** Speech-to-text inside the keyboard (no dialog), with live partial results. */
class VoiceInput(private val ctx: Context, private val cb: Callback) {

    interface Callback {
        fun onVoiceListening(listening: Boolean)
        fun onVoicePartial(text: String)
        fun onVoiceResult(text: String)
        fun onVoiceError(message: String, needsPermission: Boolean)
    }

    private var recognizer: SpeechRecognizer? = null
    var isListening = false
        private set

    fun hasPermission() = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Needs the <queries> entry in the manifest on Android 11+, otherwise this is always false. */
    fun isAvailable() = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun start(language: Locale = Locale.getDefault()) {
        if (isListening) return
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply { setRecognitionListener(listener) }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            isListening = true
            cb.onVoiceListening(true)
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            cb.onVoiceListening(false)
            cb.onVoiceError("Couldn't start voice typing", false)
        }
    }

    fun stop() {
        try { recognizer?.stopListening() } catch (e: Exception) {}
    }

    fun cancel() {
        try { recognizer?.cancel() } catch (e: Exception) {}
        if (isListening) {
            isListening = false
            cb.onVoiceListening(false)
        }
    }

    fun destroy() {
        cancel()
        try { recognizer?.destroy() } catch (e: Exception) {}
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }?.let { cb.onVoicePartial(it) }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            cb.onVoiceListening(false)
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) cb.onVoiceResult(text)
        }

        override fun onError(error: Int) {
            isListening = false
            cb.onVoiceListening(false)
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> cb.onVoiceError("Microphone permission needed", true)
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> cb.onVoiceError("Didn't catch that. Tap the mic and try again.", false)
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    cb.onVoiceError("Voice typing requires an internet connection or downloaded speech language pack.", false)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    try { recognizer?.cancel() } catch (e: Exception) {}
                    cb.onVoiceError("Voice engine busy, tap the mic again.", false)
                }
                SpeechRecognizer.ERROR_AUDIO -> cb.onVoiceError("Microphone is being used by another app.", false)
                SpeechRecognizer.ERROR_CLIENT -> {}  // happens after a manual stop; ignore
                else -> cb.onVoiceError("Voice typing error ($error)", false)
            }
        }
    }
}
