package com.aieverywhere.keyboard.keyboard

import java.util.Locale

enum class KeyType { CHAR, SHIFT, DELETE, SPACE, ENTER, TO_SYMBOLS, TO_MORE, TO_BASE, EMOJI, LANGUAGE, TO_NUMBER, SPACER, GIF }

/**
 * @param hint  character committed on long-press, shown small in the key's corner (GBoard style).
 * @param special  draw with the darker "function key" background.
 */
data class KeyDef(
    val label: String,
    val type: KeyType = KeyType.CHAR,
    val weight: Float = 1f,
    val hint: String? = null,
    val special: Boolean = false,
    val alts: String = ""
)

enum class Page { LETTERS, SYMBOLS, MORE_SYMBOLS, NUMBER, PHONE }

enum class FieldKind { TEXT, EMAIL, URL, NUMBER, PHONE }

object KeyboardLayouts {

    private fun getAlts(c: String): String = when (c.lowercase()) {
        "$" -> "¢£€¥₱₹"
        "₹" -> "¢£€¥₱$"
        "a" -> "àáâäæãåā"
        "e" -> "èéêëē"
        "i" -> "ìíîïī"
        "o" -> "òóôöœõō"
        "u" -> "ùúûüū"
        "c" -> "ç"
        "n" -> "ñ"
        "s" -> "ß"
        "-" -> "—–"
        "?" -> "¿"
        "!" -> "¡"
        "'" -> "‘’"
        "\"" -> "“”"
        else -> ""
    }

    private fun chars(s: String, hints: String? = null): List<KeyDef> =
        s.split(" ").mapIndexed { i, c ->
            val h = hints?.split(" ")?.getOrNull(i)?.takeIf { it != "_NONE_" }
            KeyDef(c, hint = h, alts = getAlts(c))
        }

    /** India shows ₹ where US GBoard shows $ (and $ moves to the second symbols page). */
    private fun mainCurrency(): String = if (Locale.getDefault().country.equals("IN", true)) "₹" else "$"
    private fun otherCurrency(): String = if (mainCurrency() == "₹") "$" else "€"

    private fun bottomRow(
        first: KeyDef,
        kind: FieldKind,
        left: String = ",",
        right: String = ".",
        showLanguageKey: Boolean = false,
        isSymbolsPage: Boolean = false
    ): List<KeyDef> {
        val l = when (kind) {
            FieldKind.EMAIL -> "@"
            FieldKind.URL -> "/"
            else -> left
        }
        val actionKey = if (isSymbolsPage) KeyDef("12\n34", KeyType.TO_NUMBER, weight = 1.0f, special = true)
                        else KeyDef("", KeyType.EMOJI, weight = 1.0f, special = true)
        
        return listOf(
            first,
            KeyDef(l, weight = 1.0f, hint = if (kind == FieldKind.EMAIL || kind == FieldKind.URL) "," else null),
            actionKey,
            KeyDef("", KeyType.SPACE, weight = 4.0f),
            KeyDef(right, weight = 1.0f),
            KeyDef("", KeyType.ENTER, weight = 1.5f)
        )
    }

    fun rows(page: Page, kind: FieldKind, numberRow: Boolean, showLanguageKey: Boolean = false): List<List<KeyDef>> = when (page) {
        Page.LETTERS -> {
            val rows = mutableListOf<List<KeyDef>>()
            if (numberRow) rows += chars("1 2 3 4 5 6 7 8 9 0")
            rows += chars("q w e r t y u i o p", if (numberRow) null else "1 2 3 4 5 6 7 8 9 0")
            rows += listOf(KeyDef("", KeyType.SPACER, 0.5f)) +
                chars("a s d f g h j k l") +
                listOf(KeyDef("", KeyType.SPACER, 0.5f))
            rows += listOf(KeyDef("", KeyType.SHIFT, 1.5f, special = true)) +
                chars("z x c v b n m") +
                listOf(KeyDef("", KeyType.DELETE, 1.5f, special = true))
            rows += bottomRow(KeyDef("?123", KeyType.TO_SYMBOLS, 1.5f, special = true), kind, showLanguageKey = showLanguageKey)
            rows
        }
        Page.SYMBOLS -> listOf(
            chars("1 2 3 4 5 6 7 8 9 0"),
            chars("@ # ${mainCurrency()} _ & - + ( ) /"),
            listOf(KeyDef("=\\<", KeyType.TO_MORE, 1.5f, special = true)) +
                chars("* \" ' : ; ! ?") +
                listOf(KeyDef("", KeyType.DELETE, 1.5f, special = true)),
            bottomRow(KeyDef(baseLabel(kind), KeyType.TO_BASE, 1.5f, special = true), kind, isSymbolsPage = true)
        )
        Page.MORE_SYMBOLS -> listOf(
            chars("~ ` | • √ π ÷ × ¶ ∆"),
            chars("£ ${otherCurrency()} ¢ ¥ ^ ° = { } \\"),
            listOf(KeyDef("?123", KeyType.TO_SYMBOLS, 1.5f, special = true)) +
                chars("% © ® ™ ✓ [ ]") +
                listOf(KeyDef("", KeyType.DELETE, 1.5f, special = true)),
            bottomRow(KeyDef(baseLabel(kind), KeyType.TO_BASE, 1.5f, special = true), kind, "<", ">", isSymbolsPage = true)
        )
        Page.NUMBER -> listOf(
            listOf(KeyDef("+", special = true), KeyDef("1"), KeyDef("2"), KeyDef("3"), KeyDef("%", special = true)),
            listOf(KeyDef("-", special = true), KeyDef("4"), KeyDef("5"), KeyDef("6"), KeyDef("_", special = true)),
            listOf(KeyDef("*", special = true), KeyDef("7"), KeyDef("8"), KeyDef("9"), KeyDef("", KeyType.DELETE, special = true)),
            listOf(
                KeyDef("ABC", KeyType.TO_BASE, special = true), 
                KeyDef(",", special = true, weight = 0.5f), 
                KeyDef("!?#", KeyType.TO_SYMBOLS, special = true, weight = 0.5f), 
                KeyDef("0"), 
                KeyDef("=", special = true, weight = 0.5f), 
                KeyDef(".", special = true, weight = 0.5f), 
                KeyDef("", KeyType.ENTER, special = true)
            )
        )
        Page.PHONE -> listOf(
            chars("1 2 3") + KeyDef("+", special = true),
            chars("4 5 6") + KeyDef("", KeyType.SPACE, special = true),
            chars("7 8 9") + KeyDef("", KeyType.DELETE, special = true),
            listOf(KeyDef("*", special = true, hint = "("), KeyDef("0", hint = "+"), KeyDef("#", special = true, hint = ")"), KeyDef("", KeyType.ENTER))
        )
    }

    private fun baseLabel(kind: FieldKind) = if (kind == FieldKind.NUMBER || kind == FieldKind.PHONE) "123" else "ABC"
}



