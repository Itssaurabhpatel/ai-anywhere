package com.aieverywhere.keyboard.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object HybridEngineController {
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun executeLocalRuleEngine(actionType: String, text: String): String {
        val trimmed = text.trim()
        return when (actionType) {
            "FIX_GRAMMAR" -> trimmed.replace("\\bi\\b".toRegex(), "I")
                .replace("\\s+".toRegex(), " ")
                .replace(" ,", ",")
                .let { if (!it.endsWith(".")) "$it." else it }
            "REWRITE" -> "Local Polished: $trimmed"
            "SMART_REPLY" -> "Thanks, got it!"
            else -> trimmed
        }
    }
}
