package com.aieverywhere.keyboard.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build

/** All colours the keyboard uses. Everything on screen is painted from one of these. */
data class KeyboardTheme(
    val id: String,
    val background: Int,
    val keyBg: Int,
    val specialKeyBg: Int,
    val keyText: Int,
    val hintText: Int,
    val accent: Int,        // enter key, active shift, selected tabs
    val accentText: Int,
    val icon: Int,
    val pressed: Int,
    val divider: Int,
    val isDark: Boolean,
    val keyBorders: Boolean = false,
    val bgImagePath: String? = null,
    val bgImageDim: Int = 35,
    /** 0 = show the top of the photo, 50 = centre, 100 = bottom. */
    val bgImageFocus: Int = 50,
    /** Opaque colour for the key-press balloon (keys may be see-through on photo themes). */
    val previewBg: Int = keyBg
)

/** Reads/writes keyboard look & feel settings (shared with the app's ThemeActivity). */
object ThemeManager {
    const val PREFS = "AIEverywherePrefs"

    const val KEY_THEME_ID = "KB_THEME_ID"
    const val KEY_CUSTOM_BG = "KB_CUSTOM_BG"
    const val KEY_CUSTOM_KEY = "KB_CUSTOM_KEY"
    const val KEY_CUSTOM_TEXT = "KB_CUSTOM_TEXT"
    const val KEY_CUSTOM_ACCENT = "KB_CUSTOM_ACCENT"
    const val KEY_BG_IMAGE = "KB_BG_IMAGE"
    const val KEY_BG_DIM = "KB_BG_DIM"
    const val KEY_BG_FOCUS = "KB_BG_FOCUS"      // photo position 0..100
    const val KEY_KEY_OPACITY = "KB_KEY_OPACITY" // photo theme key opacity 0..100
    const val KEY_BORDERS = "KB_KEY_BORDERS"
    const val KEY_HEIGHT = "KB_HEIGHT"          // small | normal | large | xlarge
    const val KEY_POPUP = "KB_KEY_POPUP"
    const val KEY_SOUND = "KB_KEY_SOUND"
    const val KEY_NUMBER_ROW = "KB_NUMBER_ROW"
    const val KEY_AUTO_CAPS = "KB_AUTO_CAPS"
    const val KEY_HAPTIC = "HAPTIC_FEEDBACK"     // existing setting from SettingsActivity

    /** id to display name, in the order shown in ThemeActivity. */
    val PRESETS = listOf(
        "system" to "Device default",
        "photo" to "Your photo",
        "light" to "Light",
        "dark" to "Dark",
        "blue" to "Ocean Blue",
        "green" to "Forest Green",
        "pink" to "Blossom Pink",
        "purple" to "Royal Purple",
        "amoled" to "AMOLED Black",
        "custom" to "Custom"
    )

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSystemDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Changes whenever anything that affects how the keyboard looks changes -> keyboard rebuilds. */
    fun stamp(ctx: Context): String {
        val p = prefs(ctx)
        return listOf(
            p.getString(KEY_THEME_ID, "system"), p.getInt(KEY_CUSTOM_BG, 0), p.getInt(KEY_CUSTOM_KEY, 0),
            p.getInt(KEY_CUSTOM_TEXT, 0), p.getInt(KEY_CUSTOM_ACCENT, 0), p.getString(KEY_BG_IMAGE, ""),
            p.getInt(KEY_BG_DIM, 35), p.getInt(KEY_BG_FOCUS, 50), p.getInt(KEY_KEY_OPACITY, 25),
            p.getString(KEY_BG_IMAGE, null)?.let { java.io.File(it).lastModified() },
            p.getBoolean(KEY_BORDERS, false), p.getString(KEY_HEIGHT, "normal"),
            p.getBoolean(KEY_NUMBER_ROW, false), isSystemDark(ctx)
        ).joinToString("|")
    }

    /** Key height in dp (GBoard "normal" is roughly 44dp keys with ~10dp row gaps). */
    fun keyHeightDp(ctx: Context): Int = when (prefs(ctx).getString(KEY_HEIGHT, "normal")) {
        "small" -> 38
        "large" -> 50
        "xlarge" -> 56
        else -> 44
    }

    /** @param idOverride resolve a specific preset (used for the previews in ThemeActivity). */
    fun resolve(ctx: Context, idOverride: String? = null): KeyboardTheme {
        val p = prefs(ctx)
        val id = idOverride ?: p.getString(KEY_THEME_ID, "system") ?: "system"
        val borders = p.getBoolean(KEY_BORDERS, false)
        val base = when (id) {
            "light" -> light()
            "dark" -> dark()
            "photo" -> photo(ctx) ?: deviceDefault(ctx)
            "blue" -> tinted("blue", 0xFF0D2B45.toInt(), 0xFF1B4F72.toInt(), 0xFF153E5C.toInt(), 0xFF4FC3F7.toInt())
            "green" -> tinted("green", 0xFF16302B.toInt(), 0xFF24503F.toInt(), 0xFF1D4034.toInt(), 0xFF66BB6A.toInt())
            "pink" -> pastel("pink", 0xFFFCE4EC.toInt(), 0xFFFFFFFF.toInt(), 0xFFF8BBD0.toInt(), 0xFFD81B60.toInt())
            "purple" -> tinted("purple", 0xFF261A3D.toInt(), 0xFF3D2A63.toInt(), 0xFF30214F.toInt(), 0xFFB388FF.toInt())
            "amoled" -> tinted("amoled", Color.BLACK, 0xFF1A1A1A.toInt(), 0xFF111111.toInt(), 0xFF8AB4F8.toInt())
            "custom" -> custom(ctx)
            else -> deviceDefault(ctx)   // "system" (and the old "dynamic" id)
        }
        return base.copy(id = id, keyBorders = borders)
    }

    /**
     * Follows the phone: light/dark from the system setting and, on Android 12+, the Material You
     * colours taken from the wallpaper (the same thing GBoard's default theme does).
     */
    fun deviceDefault(ctx: Context): KeyboardTheme =
        dynamic(ctx) ?: if (isSystemDark(ctx)) dark() else light()

    fun photoPath(ctx: Context): String? =
        prefs(ctx).getString(KEY_BG_IMAGE, null)?.takeIf { it.isNotBlank() && java.io.File(it).exists() }

    // ------------------------------------------------------------------ photo theme

    private data class PhotoColors(val luminance: Float, val average: Int, val vibrant: Int)
    private var photoCache: Pair<String, PhotoColors>? = null

    /** Average brightness + the most vivid colour of the photo (sampled from a tiny copy). */
    private fun analyse(path: String): PhotoColors? {
        val key = path + "@" + java.io.File(path).lastModified()
        photoCache?.let { if (it.first == key) return it.second }
        val bmp = Ui.decodeScaled(path, 96) ?: return null
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        var bestScore = -1f; var vibrant = 0xFFFFA000.toInt()
        val hsv = FloatArray(3)
        val stepX = maxOf(1, bmp.width / 32); val stepY = maxOf(1, bmp.height / 32)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val c = bmp.getPixel(x, y)
                r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++
                Color.colorToHSV(c, hsv)
                val score = hsv[1] * hsv[2]
                if (score > bestScore && hsv[2] > 0.35f) { bestScore = score; vibrant = c }
                x += stepX
            }
            y += stepY
        }
        bmp.recycle()
        if (n == 0) return null
        val avg = Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        val lum = ((0.299 * Color.red(avg) + 0.587 * Color.green(avg) + 0.114 * Color.blue(avg)) / 255.0).toFloat()
        if (bestScore < 0.18f) vibrant = 0xFF8AB4F8.toInt()   // photo is grey-ish: use a calm blue accent
        return PhotoColors(lum, avg, vibrant or 0xFF000000.toInt()).also { photoCache = key to it }
    }

    /** GBoard-style photo theme: see-through keys, text colour chosen from the photo's brightness. */
    private fun photo(ctx: Context): KeyboardTheme? {
        val path = photoPath(ctx) ?: return null
        val p = prefs(ctx)
        val colors = analyse(path) ?: return null
        val dim = p.getInt(KEY_BG_DIM, 35).coerceIn(0, 90) / 100f
        val shownLum = colors.luminance * (1f - dim)
        val darkPhoto = shownLum < 0.42f   // below this white text reads best, above it dark text
        val opacity = p.getInt(KEY_KEY_OPACITY, 25).coerceIn(0, 100) / 100f
        val keyTint = if (darkPhoto) Color.WHITE else Color.BLACK
        val keyBg = withAlpha(Color.WHITE, if (darkPhoto) 0.06f + opacity * 0.5f else 0.25f + opacity * 0.6f)
        val special = withAlpha(keyTint, if (darkPhoto) 0.04f + opacity * 0.3f else 0.06f + opacity * 0.25f)
        val text = if (darkPhoto) 0xFFFFFFFF.toInt() else 0xFF1F1F1F.toInt()
        val accent = colors.vibrant
        return KeyboardTheme(
            id = "photo",
            background = blend(colors.average, Color.BLACK, dim),
            keyBg = keyBg,
            specialKeyBg = special,
            keyText = text,
            hintText = withAlpha(text, 0.7f),
            accent = accent,
            accentText = if (isLight(accent)) 0xFF111111.toInt() else Color.WHITE,
            icon = text,
            pressed = withAlpha(keyTint, 0.35f),
            divider = withAlpha(text, 0.25f),
            isDark = darkPhoto,
            bgImagePath = path,
            bgImageDim = p.getInt(KEY_BG_DIM, 35),
            bgImageFocus = p.getInt(KEY_BG_FOCUS, 50),
            previewBg = if (darkPhoto) 0xFF303134.toInt() else Color.WHITE
        )
    }

    // GBoard-like light theme
    fun light() = KeyboardTheme(
        id = "light", background = 0xFFECEFF1.toInt(), keyBg = Color.WHITE, specialKeyBg = 0xFFD5DADF.toInt(),
        keyText = 0xFF202124.toInt(), hintText = 0xFF80868B.toInt(), accent = 0xFF1A73E8.toInt(),
        accentText = Color.WHITE, icon = 0xFF3C4043.toInt(), pressed = 0xFFBDC1C6.toInt(),
        divider = 0x22000000, isDark = false
    )

    // GBoard-like dark theme
    fun dark() = KeyboardTheme(
        id = "dark", background = 0xFF202124.toInt(), keyBg = 0xFF3C4043.toInt(), specialKeyBg = 0xFF2D2F33.toInt(),
        keyText = 0xFFE8EAED.toInt(), hintText = 0xFF9AA0A6.toInt(), accent = 0xFF8AB4F8.toInt(),
        accentText = 0xFF202124.toInt(), icon = 0xFFDADCE0.toInt(), pressed = 0xFF5F6368.toInt(),
        divider = 0x33FFFFFF, isDark = true
    )

    private fun tinted(id: String, bg: Int, key: Int, special: Int, accent: Int) = KeyboardTheme(
        id = id, background = bg, keyBg = key, specialKeyBg = special, keyText = 0xFFF1F3F4.toInt(),
        hintText = 0xFFB0B7BD.toInt(), accent = accent, accentText = if (isLight(accent)) 0xFF111111.toInt() else Color.WHITE,
        icon = 0xFFE0E3E6.toInt(), pressed = blend(key, Color.WHITE, 0.18f), divider = 0x33FFFFFF, isDark = true
    )

    private fun pastel(id: String, bg: Int, key: Int, special: Int, accent: Int) = KeyboardTheme(
        id = id, background = bg, keyBg = key, specialKeyBg = special, keyText = 0xFF2B2B2B.toInt(),
        hintText = 0xFF8A7F84.toInt(), accent = accent, accentText = Color.WHITE, icon = 0xFF4A3F44.toInt(),
        pressed = blend(special, Color.BLACK, 0.12f), divider = 0x22000000, isDark = false
    )

    /** Material You colours taken from the device wallpaper (Android 12+). */
    private fun dynamic(ctx: Context): KeyboardTheme? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return try {
            val dark = isSystemDark(ctx)
            fun c(id: Int) = ctx.getColor(id)
            if (dark) KeyboardTheme(
                id = "dynamic",
                background = c(android.R.color.system_neutral1_900),
                keyBg = c(android.R.color.system_neutral1_700),
                specialKeyBg = c(android.R.color.system_neutral2_800),
                keyText = c(android.R.color.system_neutral1_50),
                hintText = c(android.R.color.system_neutral2_300),
                accent = c(android.R.color.system_accent1_200),
                accentText = c(android.R.color.system_accent1_800),
                icon = c(android.R.color.system_neutral2_100),
                pressed = c(android.R.color.system_neutral1_500),
                divider = 0x33FFFFFF, isDark = true
            ) else KeyboardTheme(
                id = "dynamic",
                background = c(android.R.color.system_neutral1_50),
                keyBg = c(android.R.color.system_neutral1_10),
                specialKeyBg = c(android.R.color.system_accent2_100),
                keyText = c(android.R.color.system_neutral1_900),
                hintText = c(android.R.color.system_neutral2_500),
                accent = c(android.R.color.system_accent1_600),
                accentText = c(android.R.color.system_accent1_0),
                icon = c(android.R.color.system_neutral2_700),
                pressed = c(android.R.color.system_accent2_200),
                divider = 0x22000000, isDark = false
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun custom(ctx: Context): KeyboardTheme {
        val p = prefs(ctx)
        val bg = p.getInt(KEY_CUSTOM_BG, 0xFF263238.toInt())
        val key = p.getInt(KEY_CUSTOM_KEY, 0xFF37474F.toInt())
        val dark = !isLight(bg)
        val text = p.getInt(KEY_CUSTOM_TEXT, if (isLight(key)) 0xFF202124.toInt() else 0xFFF1F3F4.toInt())
        val accent = p.getInt(KEY_CUSTOM_ACCENT, 0xFFFFA000.toInt())
        return KeyboardTheme(
            id = "custom", background = bg, keyBg = key,
            specialKeyBg = blend(key, if (isLight(key)) Color.BLACK else Color.WHITE, 0.10f),
            keyText = text, hintText = withAlpha(text, 0.6f), accent = accent,
            accentText = if (isLight(accent)) 0xFF111111.toInt() else Color.WHITE,
            icon = text, pressed = blend(key, if (isLight(key)) Color.BLACK else Color.WHITE, 0.22f),
            divider = if (dark) 0x33FFFFFF else 0x22000000, isDark = dark
        )
    }

    fun isLight(color: Int): Boolean {
        val l = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        return l > 0.6
    }

    fun blend(a: Int, b: Int, ratio: Float): Int {
        val inv = 1f - ratio
        return Color.argb(
            (Color.alpha(a) * inv + Color.alpha(b) * ratio).toInt(),
            (Color.red(a) * inv + Color.red(b) * ratio).toInt(),
            (Color.green(a) * inv + Color.green(b) * ratio).toInt(),
            (Color.blue(a) * inv + Color.blue(b) * ratio).toInt()
        )
    }

    fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((255 * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
