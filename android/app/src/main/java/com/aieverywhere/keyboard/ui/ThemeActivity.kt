package com.aieverywhere.keyboard.ui

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.aieverywhere.keyboard.R
import com.aieverywhere.keyboard.keyboard.FieldKind
import com.aieverywhere.keyboard.keyboard.KeyType
import com.aieverywhere.keyboard.keyboard.KeyboardActionListener
import com.aieverywhere.keyboard.keyboard.KeyboardView
import com.aieverywhere.keyboard.keyboard.ThemeManager
import com.aieverywhere.keyboard.keyboard.Ui
import java.io.File
import java.io.FileOutputStream

/** Keyboard look & feel: device/Material You/preset themes, custom colours + photo, key size and behaviour. */
@SuppressLint("SetTextI18n", "UseSwitchCompatOrMaterialCode")
class ThemeActivity : AppCompatActivity() {

    private val prefs by lazy { ThemeManager.prefs(this) }
    private lateinit var previewHost: FrameLayout
    private lateinit var presetGrid: GridLayout
    private lateinit var customSection: LinearLayout
    private lateinit var photoSection: LinearLayout

    private val palette = intArrayOf(
        0xFF000000.toInt(), 0xFF202124.toInt(), 0xFF263238.toInt(), 0xFF37474F.toInt(), 0xFF546E7A.toInt(), 0xFF9E9E9E.toInt(),
        0xFFECEFF1.toInt(), 0xFFFFFFFF.toInt(), 0xFFB71C1C.toInt(), 0xFFE53935.toInt(), 0xFFF06292.toInt(), 0xFFFCE4EC.toInt(),
        0xFF4A148C.toInt(), 0xFF8E24AA.toInt(), 0xFFB388FF.toInt(), 0xFF1A237E.toInt(), 0xFF1A73E8.toInt(), 0xFF4FC3F7.toInt(),
        0xFF004D40.toInt(), 0xFF00897B.toInt(), 0xFF1B5E20.toInt(), 0xFF43A047.toInt(), 0xFFA5D6A7.toInt(), 0xFFF57F17.toInt(),
        0xFFFFA000.toInt(), 0xFFFFD54F.toInt(), 0xFFE65100.toInt(), 0xFF795548.toInt(), 0xFFD7CCC8.toInt(), 0xFFFFF8E1.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F7FA"))
        }
        root.addView(header())
        previewHost = FrameLayout(this)
        root.addView(previewHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        content.addView(section("THEME"))
        presetGrid = GridLayout(this).apply { columnCount = 2 }
        content.addView(presetGrid)
        photoSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(photoSection)
        customSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(customSection)

        content.addView(section("KEYS"))
        content.addView(card().apply {
            addView(label("Key size"))
            val sizes = listOf("small" to "Small", "normal" to "Normal", "large" to "Large", "xlarge" to "Extra large")
            addView(RadioGroup(this@ThemeActivity).apply {
                orientation = RadioGroup.HORIZONTAL
                val current = prefs.getString(ThemeManager.KEY_HEIGHT, "normal")
                sizes.forEach { (id, name) ->
                    addView(RadioButton(this@ThemeActivity).apply {
                        text = name
                        textSize = 13f
                        this.id = View.generateViewId()
                        isChecked = id == current
                        setOnCheckedChangeListener { _, on -> if (on) save { putString(ThemeManager.KEY_HEIGHT, id) } }
                    })
                }
            })
            addView(switchRow("Number row", "Always show 1–0 above the letters", ThemeManager.KEY_NUMBER_ROW, false))
            addView(switchRow("Key borders", "Outline around every key", ThemeManager.KEY_BORDERS, false))
            addView(switchRow("Popup on keypress", "Show the enlarged letter while typing", ThemeManager.KEY_POPUP, true))
            addView(switchRow("Auto-capitalization", "Capitalise the first word of each sentence", ThemeManager.KEY_AUTO_CAPS, true))
            addView(switchRow("Vibrate on keypress", "Haptic feedback", ThemeManager.KEY_HAPTIC, true))
            addView(switchRow("Sound on keypress", "Click sound while typing", ThemeManager.KEY_SOUND, false))
        })
        refresh()
    }

    // ------------------------------------------------------------------ UI pieces

    private fun dp(v: Int) = Ui.dp(this, v.toFloat())

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(Color.WHITE)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        gravity = Gravity.CENTER_VERTICAL
        
        addView(ImageView(this@ThemeActivity).apply {
            setImageResource(R.drawable.ic_back_modern)
            setColorFilter(Color.parseColor("#1C1C1E"))
            setOnClickListener { finish() }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(16) })
        
        addView(TextView(this@ThemeActivity).apply {
            text = "Keyboard theme & layout"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1C1E"))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun section(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#8E8E93"))
        setPadding(dp(6), dp(18), 0, dp(8))
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        textSize = 15f
        setTextColor(Color.parseColor("#1C1C1E"))
        setPadding(0, dp(6), 0, dp(4))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.rounded(Color.WHITE, Ui.dpf(this@ThemeActivity, 14f))
        setPadding(dp(16), dp(10), dp(16), dp(12))
    }

    private fun switchRow(title: String, sub: String, key: String, def: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(8), 0, dp(8))
        addView(LinearLayout(this@ThemeActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title).apply { setPadding(0, 0, 0, 0) })
            addView(TextView(this@ThemeActivity).apply { text = sub; textSize = 12f; setTextColor(Color.parseColor("#8E8E93")) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Switch(this@ThemeActivity).apply {
            isChecked = prefs.getBoolean(key, def)
            setOnCheckedChangeListener { _, on -> save { putBoolean(key, on) } }
        })
    }

    private fun save(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        refresh()
    }

    // ------------------------------------------------------------------ refresh

    private fun refresh() {
        // Live preview uses the real keyboard view with the saved settings.
        val theme = ThemeManager.resolve(this)
        previewHost.removeAllViews()
        val preview = KeyboardView(this, theme, object : KeyboardActionListener {
            override fun onKeyText(text: String) {}
            override fun onKeyDelete(wholeWord: Boolean) {}
            override fun onKeyEnter() {}
            override fun onKeyEmoji() {}
            override fun onCursorMove(steps: Int) {}
            override fun onShiftWithSelection() = false
            override fun onSpaceLongPress() {}
            override fun onKeyFeedback(view: View, type: KeyType) {}
            override fun onLanguageKey() {}
            override fun onGifToggle() {}
        }).apply {
            numberRow = prefs.getBoolean(ThemeManager.KEY_NUMBER_ROW, false)
            popupEnabled = false
            configure(FieldKind.TEXT)
        }
        previewHost.setBackgroundColor(theme.background)
        theme.bgImagePath?.let { path ->
            Ui.photoDrawable(path, theme.bgImageDim, theme.bgImageFocus, 1080)?.let { previewHost.background = it }
        }
        previewHost.addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        buildPresets()
        buildPhoto()
        buildCustom()
    }

    private fun buildPresets() {
        presetGrid.removeAllViews()
        val selected = prefs.getString(ThemeManager.KEY_THEME_ID, "system")
        val w = (resources.displayMetrics.widthPixels - dp(32)) / 2 - dp(8)
        val photo = ThemeManager.photoPath(this)
        for ((id, name) in ThemeManager.PRESETS) {
            val isSelected = id == selected || (id == "system" && selected == "dynamic")
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                minimumHeight = dp(78)
            }
            val border = if (isSelected) Color.parseColor("#7B1FA2") else 0x22000000
            val borderW = dp(if (isSelected) 3 else 1)
            if (id == "photo" && photo == null) {
                // No photo yet: an "upload" tile
                tile.gravity = Gravity.CENTER
                tile.background = Ui.rounded(Color.WHITE, Ui.dpf(this, 12f), Color.parseColor("#7B1FA2"), dp(1))
                tile.addView(TextView(this).apply {
                    text = "＋  Upload photo"
                    textSize = 14f
                    setTextColor(Color.parseColor("#7B1FA2"))
                    gravity = Gravity.CENTER
                })
                tile.addView(TextView(this).apply {
                    text = "Use your own picture"
                    textSize = 11f
                    setTextColor(Color.parseColor("#8E8E93"))
                    gravity = Gravity.CENTER
                })
                tile.setOnClickListener { pickImage() }
            } else {
                val sample = ThemeManager.resolve(this, id)
                val bgDrawable = if (id == "photo" && photo != null)
                    Ui.photoDrawable(photo, sample.bgImageDim, sample.bgImageFocus, 400) else null
                tile.background = if (bgDrawable != null) {
                    android.graphics.drawable.LayerDrawable(arrayOf(bgDrawable, Ui.rounded(0, Ui.dpf(this, 12f), border, borderW)))
                } else Ui.rounded(sample.background, Ui.dpf(this, 12f), border, borderW)
                tile.clipToOutline = true
                val keys = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                listOf(sample.keyBg, sample.keyBg, sample.specialKeyBg, sample.accent).forEach { c ->
                    keys.addView(View(this).apply { background = Ui.rounded(c, Ui.dpf(this@ThemeActivity, 4f)) },
                        LinearLayout.LayoutParams(0, dp(22), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
                }
                tile.addView(keys)
                tile.addView(TextView(this).apply {
                    text = name
                    textSize = 12f
                    setTextColor(sample.keyText)
                    setPadding(dp(2), dp(8), 0, 0)
                })
                if (id == "system") tile.addView(TextView(this).apply {
                    text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Light/dark + wallpaper colours" else "Follows light/dark mode"
                    textSize = 10f
                    setTextColor(sample.hintText)
                    setPadding(dp(2), 0, 0, 0)
                })
                tile.setOnClickListener { save { putString(ThemeManager.KEY_THEME_ID, id) } }
            }
            presetGrid.addView(tile, GridLayout.LayoutParams().apply { width = w; setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
    }

    private fun slider(title: String, key: String, def: Int, max: Int, left: String, right: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(2))
            addView(label(title))
            addView(SeekBar(this@ThemeActivity).apply {
                this.max = max
                progress = prefs.getInt(key, def)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {}
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) { save { putInt(key, progress) } }
                })
            })
            addView(LinearLayout(this@ThemeActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(this@ThemeActivity).apply { text = left; textSize = 11f; setTextColor(Color.parseColor("#8E8E93")) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(this@ThemeActivity).apply { text = right; textSize = 11f; setTextColor(Color.parseColor("#8E8E93")) })
            })
        }

    /** Controls for the uploaded photo (only shown once a photo exists). */
    private fun buildPhoto() {
        photoSection.removeAllViews()
        val photo = ThemeManager.photoPath(this) ?: return
        photoSection.addView(section("YOUR PHOTO"))
        val c = card()
        c.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(this@ThemeActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(Ui.decodeScaled(photo, dp(120)))
                clipToOutline = true
                background = Ui.rounded(Color.LTGRAY, Ui.dpf(this@ThemeActivity, 8f))
            }, LinearLayout.LayoutParams(dp(72), dp(48)))
            addView(View(this@ThemeActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(TextView(this@ThemeActivity).apply {
                text = "Change"
                setTextColor(Color.parseColor("#7B1FA2")); textSize = 14f; setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener { pickImage() }
            })
            addView(TextView(this@ThemeActivity).apply {
                text = "Remove"
                setTextColor(Color.parseColor("#E53935")); textSize = 14f; setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener {
                    File(photo).delete()
                    save {
                        remove(ThemeManager.KEY_BG_IMAGE)
                        if (prefs.getString(ThemeManager.KEY_THEME_ID, "system") == "photo") putString(ThemeManager.KEY_THEME_ID, "system")
                    }
                }
            })
        })
        if (prefs.getString(ThemeManager.KEY_THEME_ID, "system") != "photo") {
            c.addView(TextView(this).apply {
                text = "Use this photo as the keyboard theme"
                setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
                background = Ui.rounded(Color.parseColor("#7B1FA2"), Ui.dpf(this@ThemeActivity, 20f))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setOnClickListener { save { putString(ThemeManager.KEY_THEME_ID, "photo") } }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }
        c.addView(slider("Brightness", ThemeManager.KEY_BG_DIM, 35, 80, "Bright photo", "Darker (easier to read)"))
        c.addView(slider("Key transparency", ThemeManager.KEY_KEY_OPACITY, 25, 100, "See-through keys", "Solid keys"))
        c.addView(slider("Photo position", ThemeManager.KEY_BG_FOCUS, 50, 100, "Top of photo", "Bottom of photo"))
        photoSection.addView(c)
    }

    private fun buildCustom() {
        customSection.removeAllViews()
        customSection.addView(section("CUSTOM THEME"))
        val c = card()
        val colors = listOf(
            Triple("Background", ThemeManager.KEY_CUSTOM_BG, 0xFF263238.toInt()),
            Triple("Keys", ThemeManager.KEY_CUSTOM_KEY, 0xFF37474F.toInt()),
            Triple("Key text", ThemeManager.KEY_CUSTOM_TEXT, 0xFFF1F3F4.toInt()),
            Triple("Accent (Enter, Shift)", ThemeManager.KEY_CUSTOM_ACCENT, 0xFFFFA000.toInt())
        )
        for ((name, key, def) in colors) {
            val current = prefs.getInt(key, def)
            c.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
                addView(label(name), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(View(this@ThemeActivity).apply {
                    background = Ui.rounded(current, Ui.dpf(this@ThemeActivity, 16f), 0x33000000, dp(1))
                }, LinearLayout.LayoutParams(dp(56), dp(32)))
                setOnClickListener { pickColor(name, current) { chosen -> save { putInt(key, chosen); putString(ThemeManager.KEY_THEME_ID, "custom") } } }
            })
        }
        customSection.addView(c)
    }

    private fun pickColor(title: String, current: Int, onPick: (Int) -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), 0) }
        val grid = GridLayout(this).apply { columnCount = 6 }
        var dialog: AlertDialog? = null
        for (col in palette) {
            grid.addView(View(this).apply {
                background = Ui.rounded(col, Ui.dpf(this@ThemeActivity, 18f), if (col == current) Color.parseColor("#7B1FA2") else 0x33000000, dp(if (col == current) 3 else 1))
                setOnClickListener { onPick(col); dialog?.dismiss() }
            }, GridLayout.LayoutParams().apply { width = dp(38); height = dp(38); setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        box.addView(grid)
        val hex = EditText(this).apply {
            hint = "#RRGGBB"
            setText(String.format("#%06X", 0xFFFFFF and current))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        box.addView(hex)
        dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Use hex") { _, _ ->
                try { onPick(Color.parseColor(hex.text.toString().trim())) } catch (e: Exception) {
                    Toast.makeText(this, "Invalid colour", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------ background photo

    private fun pickImage() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, REQ_IMAGE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        if (requestCode != REQ_IMAGE || resultCode != RESULT_OK) return
        Thread {
            val saved = saveBackground(uri)
            runOnUiThread {
                if (saved != null) save { putString(ThemeManager.KEY_BG_IMAGE, saved); putString(ThemeManager.KEY_THEME_ID, "photo"); putInt(ThemeManager.KEY_BG_FOCUS, 50) }
                else Toast.makeText(this, "Couldn't open that picture", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun saveBackground(uri: Uri): String? = try {
        val tmp = File(cacheDir, "bg_pick")
        contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(tmp).use { input.copyTo(it) } }
        val bmp = Ui.decodeScaled(tmp.absolutePath, 1600)
        tmp.delete()
        if (bmp == null) null else {
            val out = File(filesDir, "keyboard_bg_${System.currentTimeMillis()}.jpg")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            prefs.getString(ThemeManager.KEY_BG_IMAGE, null)?.let { File(it).delete() }
            out.absolutePath
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val REQ_IMAGE = 41
    }
}

