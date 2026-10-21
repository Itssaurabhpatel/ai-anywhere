package com.aieverywhere.keyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.aieverywhere.keyboard.R
import java.io.File

/** Clipboard history panel: recent screenshots / copied images on top, copied text below. */
@SuppressLint("ViewConstructor", "UseSwitchCompatOrMaterialCode", "SetTextI18n")
class ClipboardPanel(
    context: Context,
    private val theme: KeyboardTheme,
    private val watcher: ScreenshotWatcher,
    private val cb: Callbacks
) : LinearLayout(context) {

    interface Callbacks {
        fun onClipText(text: String)
        fun onClipImage(file: File)
        fun onClipBack()
        fun onRequestScreenshotPermission()
    }

    private val body = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        addView(header(), LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 40f)))
        addView(ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(body)
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        refresh()
    }

    private fun iconButton(res: Int, desc: String, color: Int = theme.icon, onClick: () -> Unit) = ImageView(context).apply {
        setImageResource(res)
        Ui.tint(this, color)
        val p = Ui.dp(context, 7f)
        setPadding(p, p, p, p)
        contentDescription = desc
        setOnClickListener { onClick() }
    }

    private fun header(): View {
        val h = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 4f), 0, Ui.dp(context, 4f), 0)
        }
        val size = Ui.dp(context, 36f)
        h.addView(iconButton(R.drawable.ic_back_modern, "Back") { cb.onClipBack() }, LayoutParams(size, size))
        h.addView(TextView(context).apply {
            text = "Clipboard"
            setTextColor(theme.keyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(Ui.dp(context, 6f), 0, 0, 0)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        h.addView(Switch(context).apply {
            isChecked = ClipboardStore.isEnabled(context)
            contentDescription = "Clipboard history on/off"
            setOnCheckedChangeListener { _, on ->
                ClipboardStore.setEnabled(context, on)
                refresh()
            }
        })
        h.addView(iconButton(R.drawable.ic_delete, "Clear all") {
            ClipboardStore.clear(context)
            refresh()
        }, LayoutParams(size, size))
        return h
    }

    private fun label(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(theme.hintText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(Ui.dp(context, 12f), Ui.dp(context, 6f), Ui.dp(context, 12f), Ui.dp(context, 4f))
    }

    fun refresh() {
        body.removeAllViews()
        if (!ClipboardStore.isEnabled(context)) {
            body.addView(label("Clipboard history is off. Turn on the switch to keep copied text, images and screenshots."))
            return
        }
        if (!watcher.hasPermission()) {
            body.addView(TextView(context).apply {
                text = "Show my recent screenshots here  —  Allow"
                setTextColor(theme.accent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(Ui.dp(context, 12f), Ui.dp(context, 8f), Ui.dp(context, 12f), Ui.dp(context, 8f))
                setOnClickListener { cb.onRequestScreenshotPermission() }
            })
        }
        val items = ClipboardStore.all(context)
        val images = items.filter { it.type == "image" && it.path != null && File(it.path).exists() && it.isScreenshot }
        val texts = items.filter { it.type == "text" && !it.text.isNullOrBlank() }

        if (texts.isEmpty() && images.isEmpty()) {
            body.addView(TextView(context).apply {
                text = "Clipboard is empty\nCopied text and screenshots will appear here"
                gravity = Gravity.CENTER
                setTextColor(theme.hintText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, Ui.dp(context, 36f), 0, 0)
            })
            return
        }

        // 1. Recent Screenshots (Gboard style top row)
        if (images.isNotEmpty()) {
            body.addView(label("Screenshots"))
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(Ui.dp(context, 10f), 0, Ui.dp(context, 10f), 0)
            }
            val hh = Ui.dp(context, 96f)
            val ww = (hh * 0.65f).toInt()
            for (item in images) {
                val iv = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(Ui.decodeScaled(item.path!!, Ui.dp(context, 200f)))
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) =
                            outline.setRoundRect(0, 0, view.width, view.height, Ui.dpf(context, 10f))
                    }
                    contentDescription = "Screenshot"
                    setOnClickListener { cb.onClipImage(File(item.path)) }
                    setOnLongClickListener {
                        ClipboardStore.remove(context, item); refresh(); true
                    }
                }
                row.addView(iv, LayoutParams(ww, hh).apply { setMargins(Ui.dp(context, 4f), 0, Ui.dp(context, 4f), 0) })
            }
            body.addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            })
        }

        val pinnedTexts = texts.filter { it.isPinned }
        val recentTexts = texts.filter { !it.isPinned }

        // 2. Pinned Items (Gboard style)
        if (pinnedTexts.isNotEmpty()) {
            body.addView(label("Pinned"))
            for (item in pinnedTexts) addTextCard(item)
        }

        // 3. Recent Items (Gboard style with 1-hour note)
        if (recentTexts.isNotEmpty()) {
            body.addView(label("Recent"))
            body.addView(TextView(context).apply {
                text = "Unpinned items will be deleted after 1 hour"
                setTextColor(theme.hintText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(Ui.dp(context, 12f), Ui.dp(context, 0f), Ui.dp(context, 12f), Ui.dp(context, 4f))
            })
            for (item in recentTexts) addTextCard(item)
        }
    }

    /** "2:15 PM" today, "Yesterday", or "12 Sep". */
    private fun timeLabel(time: Long): String {
        val now = java.util.Calendar.getInstance()
        val c = java.util.Calendar.getInstance().apply { timeInMillis = time }
        val sameYear = now.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR)
        val dayDiff = now.get(java.util.Calendar.DAY_OF_YEAR) - c.get(java.util.Calendar.DAY_OF_YEAR)
        return when {
            sameYear && dayDiff == 0 -> java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(c.time)
            sameYear && dayDiff == 1 -> "Yesterday"
            else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(c.time)
        }
    }

    private fun addTextCard(item: ClipItem) {
        val card = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.keyBackground(context, theme, theme.keyBg, 10f)
            setPadding(Ui.dp(context, 12f), Ui.dp(context, 8f), Ui.dp(context, 4f), Ui.dp(context, 8f))
            setOnClickListener { cb.onClipText(item.text!!) }
        }
        val col = LinearLayout(context).apply { orientation = VERTICAL }
        col.addView(TextView(context).apply {
            text = item.text
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(theme.keyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        })
        col.addView(TextView(context).apply {
            text = timeLabel(item.time)
            setTextColor(theme.hintText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        })
        card.addView(col, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        // Gboard-style Pin button
        val pinColor = if (item.isPinned) theme.accent else theme.hintText
        card.addView(iconButton(R.drawable.ic_kb_pin, if (item.isPinned) "Unpin" else "Pin", pinColor) {
            ClipboardStore.togglePin(context, item.id)
            refresh()
        }, LayoutParams(Ui.dp(context, 32f), Ui.dp(context, 32f)))

        // Delete button
        card.addView(iconButton(R.drawable.ic_clear, "Remove", theme.hintText) {
            ClipboardStore.remove(context, item); refresh()
        }, LayoutParams(Ui.dp(context, 32f), Ui.dp(context, 32f)))

        body.addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(Ui.dp(context, 10f), Ui.dp(context, 3f), Ui.dp(context, 10f), Ui.dp(context, 3f))
        })
    }
}
