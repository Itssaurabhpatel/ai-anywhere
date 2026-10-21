package com.aieverywhere.keyboard.network

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AiResponse(
    val resultText: String,
    val suggestions: List<String>?,
    val providerUsed: String,
    val latencyMs: Long
)

/**
 * Thrown for provider errors. [modelUnavailable] = the provider says the model id
 * no longer exists / was decommissioned, so we can retry with the next fallback model.
 */
class AiProviderException(message: String, val modelUnavailable: Boolean = false) : Exception(message)

object ApiClient {

    // ---------------------------------------------------------------------------------
    // Model ids per provider. First entry is the primary model, the rest are fallbacks
    // that are tried automatically if the provider reports the model as removed.
    // The PREFERRED_MODEL codes saved in SharedPreferences are unchanged, so existing
    // users keep their selection.
    // ---------------------------------------------------------------------------------
    private val OPENAI_MODELS = listOf("gpt-4o-mini", "gpt-4.1-mini")
    private val ANTHROPIC_MODELS = listOf("claude-sonnet-5", "claude-sonnet-4-6")
    private val GEMINI_MODELS = listOf("gemini-3.6-flash", "gemini-flash-latest")
    private val DEEPSEEK_MODELS = listOf("deepseek-v4-flash")
    private val GROQ_MODELS = listOf("openai/gpt-oss-120b", "qwen/qwen3.6-27b")

    private val XAI_MODELS = listOf("grok-4.3", "grok-4.5")
    private val CLOUDFLARE_MODELS = listOf("@cf/meta/llama-3.3-70b-instruct-fp8-fast", "@cf/meta/llama-3.1-8b-instruct")

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 45_000   // reasoning models can take a few seconds

    suspend fun executeAction(
        context: Context,
        actionType: String,
        inputText: String,
        targetTone: String? = null,
        targetLanguage: String? = null,
        customInstruction: String? = null
    ): Result<AiResponse> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("AIEverywherePrefs", Context.MODE_PRIVATE)

        fun key(name: String) = prefs.getString(name, "")?.trim() ?: ""
        val geminiKey = key("GEMINI_KEY")
        val openAiKey = key("OPENAI_KEY")
        val deepseekKey = key("DEEPSEEK_KEY")
        val anthropicKey = key("ANTHROPIC_KEY")
        val groqKey = key("GROK_KEY")          // pref name kept for backward compatibility

        val cloudflareKey = key("CLOUDFLARE_KEY")
        val preferredModel = prefs.getString("PREFERRED_MODEL", "gemini-3.6-flash") ?: "gemini-3.6-flash"

        val allKeysEmpty = listOf(geminiKey, openAiKey, deepseekKey, anthropicKey, groqKey, cloudflareKey)
            .all { it.isEmpty() }
        if (allKeysEmpty) {
            return@withContext Result.failure(Exception("Please add your API Key in the app under 'Add API' tab."))
        }

        val prefTone = (prefs.getString("AI_DEFAULT_TONE", "Auto") ?: "Auto").takeIf { it.isNotBlank() && it != "Auto" }
        val replyQuestion = prefs.getBoolean("AI_REPLY_WITH_QUESTION", false)
        var prompt = buildPrompt(actionType, inputText, targetTone ?: prefTone, targetLanguage, customInstruction, prefTone, replyQuestion)
        
        val personalContext = prefs.getString("PERSONAL_CONTEXT_DATA", "") ?: ""
        if (personalContext.isNotBlank() && prefs.getBoolean("AI_USE_PERSONAL_CONTEXT", true)) {
            prompt = "Context about the user: $personalContext\n\n" + prompt
        }
        prompt += "\n\nIMPORTANT RULE: Return plain text ONLY without any Markdown formatting (no asterisks, no hashes, no blockquotes)."
        if (replyQuestion) {
            prompt += "\n\nMANDATORY RULE: At the very end of your final response, ALWAYS attach a short, relevant follow-up question for the user."
        }

        val autoSwitch = prefs.getBoolean("AUTO_SWITCH", true)

        try {
            val (text, providerLabel) = when (preferredModel) {
                "gpt-4o-mini", "gpt-4o" -> {
                    requireKey(openAiKey, "OpenAI")
                    val models = if (preferredModel == "gpt-4o") listOf("gpt-4o") + OPENAI_MODELS else OPENAI_MODELS
                    withFallback(models, "OpenAI") { m ->
                        callOpenAiStyleApi("https://api.openai.com/v1/chat/completions", openAiKey, m, prompt, "OpenAI")
                    }
                }
                "claude-3-5-sonnet" -> {
                    requireKey(anthropicKey, "Anthropic")
                    withFallback(ANTHROPIC_MODELS, "Anthropic") { m -> callAnthropicApi(anthropicKey, m, prompt) }
                }
                "gemini-3.6-flash" -> {
                    requireKey(geminiKey, "Google Gemini")
                    withFallback(GEMINI_MODELS, "Google Gemini") { m -> callGeminiApi(geminiKey, m, prompt) }
                }
                "deepseek-chat" -> {
                    requireKey(deepseekKey, "DeepSeek")
                    withFallback(DEEPSEEK_MODELS, "DeepSeek") { m ->
                        callOpenAiStyleApi("https://api.deepseek.com/chat/completions", deepseekKey, m, prompt, "DeepSeek",
                            extras = JSONObject().put("thinking", JSONObject().put("type", "disabled")))
                    }
                }
                "grok" -> {
                    requireKey(groqKey, "Groq / Grok")
                    if (groqKey.startsWith("xai-")) {
                        // An xAI (Grok) key was pasted into this slot: talk to api.x.ai instead of Groq.
                        return@withContext finish(startTime, withFallback(XAI_MODELS, "xAI Grok") { m ->
                            callOpenAiStyleApi("https://api.x.ai/v1/chat/completions", groqKey, m, prompt, "xAI Grok")
                        })
                    }
                    withFallback(GROQ_MODELS, "Groq") { m ->
                        callOpenAiStyleApi("https://api.groq.com/openai/v1/chat/completions", groqKey, m, prompt, "Groq",
                            extras = reasoningExtras(m))
                    }
                }

                "cloudflare" -> {
                    requireKey(cloudflareKey, "Cloudflare")
                    val parts = cloudflareKey.split(":", limit = 2).map { it.trim() }
                    if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                        throw AiProviderException("Cloudflare key MUST be pasted in this exact format: ACCOUNT_ID:API_TOKEN")
                    }
                    val (accountId, apiToken) = parts
                    withFallback(CLOUDFLARE_MODELS, "Cloudflare AI") { m ->
                        // Workers AI defaults max_tokens to 256, which cuts long rewrites short.
                        callOpenAiStyleApi("https://api.cloudflare.com/client/v4/accounts/$accountId/ai/v1/chat/completions",
                            apiToken, m, prompt, "Cloudflare", extras = JSONObject().put("max_tokens", 1024))
                    }
                }
                else -> throw AiProviderException("Unknown model selected ($preferredModel). Please pick a model again in the 'Models' tab.")
            }

            if (text.isBlank()) throw AiProviderException("The AI returned an empty response. Please try again.")
            val latency = System.currentTimeMillis() - startTime
            Result.success(AiResponse(text, null, providerLabel, latency))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("The AI took too long to respond. Check your connection and try again."))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("No internet connection. Please check your network."))
        } catch (e: AiProviderException) {
            // ── Auto-Switch fallback ──────────────────────────────────────────────
            // If AUTO_SWITCH is on and the error is quota / billing / rate-limit,
            // try every other provider that has a key configured.
            val isQuotaError = e.message?.let { m ->
                m.contains("quota", ignoreCase = true) ||
                m.contains("balance", ignoreCase = true) ||
                m.contains("rate limit", ignoreCase = true) ||
                m.contains("billing", ignoreCase = true) ||
                m.contains("insufficient", ignoreCase = true) ||
                m.contains("429", ignoreCase = true)
            } == true

            if (autoSwitch && isQuotaError) {
                data class FallbackDef(val key: String, val label: String, val call: () -> Pair<String, String>)
                val candidates = listOf(
                    // 1. Gemini
                    FallbackDef(geminiKey, "Gemini") {
                        withFallback(GEMINI_MODELS, "Google Gemini") { m -> callGeminiApi(geminiKey, m, prompt) }
                    },
                    // 2. Groq
                    FallbackDef(groqKey, "Groq") {
                        withFallback(GROQ_MODELS, "Groq") { m ->
                            callOpenAiStyleApi("https://api.groq.com/openai/v1/chat/completions", groqKey, m, prompt, "Groq",
                                extras = reasoningExtras(m))
                        }
                    },
                    // 3. Anthropic
                    FallbackDef(anthropicKey, "Anthropic") {
                        withFallback(ANTHROPIC_MODELS, "Anthropic") { m -> callAnthropicApi(anthropicKey, m, prompt) }
                    },
                    // 4. OpenAI
                    FallbackDef(openAiKey, "OpenAI") {
                        withFallback(OPENAI_MODELS, "OpenAI") { m ->
                            callOpenAiStyleApi("https://api.openai.com/v1/chat/completions", openAiKey, m, prompt, "OpenAI")
                        }
                    },
                    // 5. Cloudflare
                    FallbackDef(cloudflareKey, "Cloudflare") {
                        val parts = cloudflareKey.split(":", limit = 2).map { it.trim() }
                        if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty())
                            withFallback(CLOUDFLARE_MODELS, "Cloudflare AI") { m ->
                                callOpenAiStyleApi("https://api.cloudflare.com/client/v4/accounts/${parts[0]}/ai/v1/chat/completions",
                                    parts[1], m, prompt, "Cloudflare", extras = JSONObject().put("max_tokens", 1024))
                            }
                        else throw AiProviderException("Cloudflare key format invalid")
                    },

                ).filter { it.key.isNotEmpty() }

                for (fb in candidates) {
                    try {
                        val (text, label) = fb.call()
                        if (text.isNotBlank()) {
                            val latency = System.currentTimeMillis() - startTime
                            return@withContext Result.success(AiResponse(text, null, "Auto-Switch \u2192 $label", latency))
                        }
                    } catch (_: Exception) { /* try next */ }
                }
            }
            Result.failure(Exception(e.message ?: "Check network connection or API key validity."))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception(e.message ?: "Check network connection or API key validity."))
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun finish(startTime: Long, res: Pair<String, String>): Result<AiResponse> {
        if (res.first.isBlank()) return Result.failure(Exception("The AI returned an empty response. Please try again."))
        return Result.success(AiResponse(res.first, null, res.second, System.currentTimeMillis() - startTime))
    }

    private fun requireKey(key: String, providerName: String) {
        if (key.isEmpty()) {
            throw AiProviderException("$providerName API key is missing. Please add it in the 'Add API' tab.")
        }
    }

    /** gpt-oss models reason before answering; "low" keeps the keyboard snappy. */
    private fun reasoningExtras(model: String): JSONObject? =
        if (model.contains("gpt-oss")) JSONObject().put("reasoning_effort", "low") else null

    /** Try each model in order; move to the next one only if the provider says the model is gone. */
    private fun withFallback(models: List<String>, providerName: String, call: (String) -> String): Pair<String, String> {
        var lastError: AiProviderException? = null
        for (model in models) {
            try {
                return Pair(call(model), "$providerName ($model)")
            } catch (e: AiProviderException) {
                if (!e.modelUnavailable) throw e
                lastError = e
            }
        }
        throw lastError ?: AiProviderException("No available model for $providerName.")
    }

    private fun buildPrompt(
        actionType: String,
        inputText: String,
        targetTone: String?,
        targetLanguage: String?,
        customInstruction: String?,
        prefTone: String? = null,
        replyQuestion: Boolean = false
    ): String {
        return when (actionType.uppercase()) {
            "TRANSLATE" -> {
                val lang = targetLanguage ?: "English"
                "Translate the following text into $lang. Return ONLY the translation, with no explanation or surrounding quotes:\n\n$inputText"
            }
            "REWRITE" -> {
                "Rewrite the following text to make it clearer and more engaging. Return ONLY the rewritten text:\n\n$inputText"
            }
            "GRAMMAR", "FIX", "FIX_GRAMMAR" -> {
                "Fix all spelling, punctuation, and grammar mistakes in the following text. Return ONLY the corrected text:\n\n$inputText"
            }
            "TONE" -> {
                val tone = targetTone ?: "Professional"
                "Rewrite the following text in a $tone tone. Return ONLY the updated text:\n\n$inputText"
            }
            "REPLY", "SMART_REPLY" -> {
                val toneRule = if (prefTone != null) "in a $prefTone tone" else "in the same language and tone"
                val qRule = if (replyQuestion) " End the reply with a short, relevant follow-up question." else " Do not ask a question."
                "Write one short, natural reply to the following message, $toneRule.$qRule Return ONLY the reply text:\n\n$inputText"
            }
            "SUMMARIZE" -> {
                "Summarize the following text concisely. Return ONLY the summary:\n\n$inputText"
            }
            else -> {
                val qRule = if (replyQuestion) "\n\nEnd your response with a short, relevant follow-up question." else ""
                val toneRule = if (prefTone != null) " in a $prefTone tone" else ""
                if (!customInstruction.isNullOrEmpty()) {
                    if (inputText.isBlank()) {
                        "$customInstruction$toneRule$qRule"
                    } else {
                        "$customInstruction$toneRule:\n\n$inputText$qRule"
                    }
                } else {
                    "Process the following text$toneRule:\n\n$inputText$qRule"
                }
            }
        }
    }

    /** Strips leftover markdown / reasoning tags without destroying hashtags like #weekend or C#. */
    private fun cleanText(raw: String): String {
        var text = raw
        text = text.replace(Regex("(?s)<think>.*?</think>"), "")
        text = text.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        text = text.replace(Regex("__(.*?)__"), "$1")
        text = text.replace(Regex("(?m)^#{1,6}\\s+"), "")
        text = text.replace(Regex("(?m)^>\\s?"), "")
        return text.trim()
    }

    private fun isModelUnavailable(code: Int, errText: String): Boolean {
        if (code == 404) return true
        if (code != 400 && code != 422) return false
        val t = errText.lowercase()
        val mentionsModel = t.contains("model")
        val gone = listOf("not found", "does not exist", "decommissioned", "deprecated", "not supported",
            "invalid model", "unknown model", "no such model", "not available", "model_not_found")
        return mentionsModel && gone.any { t.contains(it) }
    }

    private fun extractErrorMessage(errText: String): String {
        return try {
            val json = JSONObject(errText)
            when {
                json.optJSONObject("error") != null -> json.optJSONObject("error")?.optString("message") ?: ""
                json.has("error") -> json.optString("error")
                json.optJSONArray("errors") != null && json.getJSONArray("errors").length() > 0 ->   // Cloudflare
                    json.getJSONArray("errors").getJSONObject(0).optString("message")
                json.has("message") -> json.optString("message")
                else -> ""
            }
        } catch (e: Exception) { "" }
    }

    private fun buildProviderException(responseCode: Int, errText: String, providerName: String): AiProviderException {
        val msg = extractErrorMessage(errText)

        if (isModelUnavailable(responseCode, errText)) {
            return AiProviderException("$providerName: the model is no longer available. ${msg.take(160)}".trim(), modelUnavailable = true)
        }
        val lower = (msg + " " + errText).lowercase()
        if (lower.contains("insufficient") || lower.contains("balance") || lower.contains("quota") || lower.contains("credit")) {
            return AiProviderException("Insufficient balance or quota exceeded. Please check your billing dashboard for $providerName.")
        }
        val friendly = when (responseCode) {
            401 -> "Invalid API Key. Please update your $providerName key in the Add API tab."
            402 -> "Billing issue with $providerName. Please check your account balance."
            429 -> "Rate limit reached for $providerName. Please wait a moment and try again."
            403 -> "Permission denied for $providerName. Check your API key permissions."
            500, 502, 503, 504, 529 -> "$providerName servers are currently busy or unavailable. Try again later."
            else -> when {
                msg.contains("api key", ignoreCase = true) -> "Invalid API Key. Please update your $providerName key in the Add API tab."
                msg.isNotEmpty() -> "Error from $providerName: $msg"
                else -> "Unexpected error from $providerName (Code $responseCode): ${errText.take(200)}"
            }
        }
        return AiProviderException(friendly)
    }

    private fun post(endpoint: String, headers: Map<String, String>, payload: JSONObject, providerName: String): JSONObject {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
                return JSONObject(responseText)
            }
            val errText = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText) ?: ""
            throw buildProviderException(code, errText, providerName)
        } finally {
            conn.disconnect()
        }
    }

    // ------------------------------------------------------------------ providers

    private fun callGeminiApi(apiKey: String, model: String, prompt: String): String {
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
        }
        val json = post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            mapOf("x-goog-api-key" to apiKey), payload, "Google Gemini"
        )
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val blockReason = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw AiProviderException(if (blockReason.isNotEmpty()) "Gemini blocked this request ($blockReason)." else "Empty response from Gemini")
        }
        val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            ?: throw AiProviderException("Empty response from Gemini")
        // Newer Gemini models can return several parts (incl. thought parts) - join only the answer text.
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (p.optBoolean("thought", false)) continue
            if (p.has("text") && !p.isNull("text")) sb.append(p.getString("text"))
        }
        return cleanText(sb.toString())
    }

    private fun callOpenAiStyleApi(
        endpoint: String,
        apiKey: String,
        model: String,
        prompt: String,
        providerName: String,
        extras: JSONObject? = null
    ): String {
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
            extras?.keys()?.forEach { k -> put(k, extras.get(k)) }
        }
        val json = post(endpoint, mapOf("Authorization" to "Bearer $apiKey"), payload, providerName)
        val choices = json.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val message = choices.getJSONObject(0).optJSONObject("message")
            // optString returns the literal "null" for JSON null, so check explicitly.
            if (message != null && message.has("content") && !message.isNull("content")) {
                return cleanText(message.getString("content"))
            }
        }
        throw AiProviderException("Empty response from $providerName")
    }

    private fun callAnthropicApi(apiKey: String, model: String, prompt: String): String {
        val payload = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1024)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
        }
        val json = post(
            "https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
            payload, "Anthropic"
        )
        val content = json.optJSONArray("content") ?: throw AiProviderException("Empty response from Claude")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "text") sb.append(block.optString("text", ""))
        }
        if (sb.isEmpty()) throw AiProviderException("Empty response from Claude")
        return cleanText(sb.toString())
    }
}

