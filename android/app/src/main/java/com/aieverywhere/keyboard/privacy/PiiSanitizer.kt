package com.aieverywhere.keyboard.privacy

import java.util.regex.Pattern

object PiiSanitizer {
    private val PHONE_PATTERN = Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}")
    private val EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val CREDIT_CARD_PATTERN = Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b")

    fun sanitize(input: String): String {
        var text = input
        text = PHONE_PATTERN.matcher(text).replaceAll("[PHONE_REDACTED]")
        text = EMAIL_PATTERN.matcher(text).replaceAll("[EMAIL_REDACTED]")
        text = CREDIT_CARD_PATTERN.matcher(text).replaceAll("[CARD_REDACTED]")
        return text
    }
}
