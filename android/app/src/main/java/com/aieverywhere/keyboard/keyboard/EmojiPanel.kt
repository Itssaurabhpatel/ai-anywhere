package com.aieverywhere.keyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.emoji2.text.EmojiCompat
import com.aieverywhere.keyboard.R
import java.io.File

/**
 * GBoard-style emoji panel: category tabs, recents, only emojis this phone can actually draw,
 * plus a Stickers tab with a built-in pack and the user's own photo stickers.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class EmojiPanel(context: Context, private val theme: KeyboardTheme, private val cb: Callbacks) : LinearLayout(context) {

    interface Callbacks {
        fun onEmoji(emoji: String)
        fun onPanelDelete(wholeWord: Boolean)
        fun onBackToKeyboard()
        fun onSticker(file: File)
        fun onAddSticker()
        fun onPanelFeedback(v: View)
    }

    private val prefs = ThemeManager.prefs(context)
    private val tabs = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val grid = GridView(context)
    private var stickerMode = false
    private var category = -1           // -1 = recent
    private val checkPaint = Paint()
    private val renderable = HashMap<Int, List<String>>()
    private lateinit var btnEmojiMode: TextView
    private lateinit var btnStickerMode: ImageView

    init {
        orientation = VERTICAL
        val tabScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
        }
        addView(tabScroll, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 40f)))
        grid.apply {
            setPadding(Ui.dp(context, 4f), 0, Ui.dp(context, 4f), 0)
            clipToPadding = false
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            isVerticalScrollBarEnabled = false
            selector = android.graphics.drawable.ColorDrawable(0)
        }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(bottomBar(), LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 44f)))
        category = if (recents().isEmpty()) 0 else -1
        showEmojis()
    }

    fun refresh() {
        if (stickerMode) showStickers() else showEmojis()
    }

    // ------------------------------------------------------------------ emoji

    private fun emojiCompatReady(): Boolean = try {
        EmojiCompat.isConfigured() && EmojiCompat.get().loadState == EmojiCompat.LOAD_STATE_SUCCEEDED
    } catch (t: Throwable) {
        false
    }

    /** Hides emojis the device font can't draw (no more empty boxes on older phones). */
    private fun supported(index: Int): List<String> = renderable.getOrPut(index) {
        val all = EmojiData.categories[index].second
        if (emojiCompatReady()) all else all.filter { checkPaint.hasGlyph(it) }
    }

    private fun recents(): List<String> =
        prefs.getString(PREF_RECENT, "")?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()

    private fun pushRecent(e: String) {
        val list = (listOf(e) + recents().filter { it != e }).take(32)
        prefs.edit().putString(PREF_RECENT, list.joinToString(" ")).apply()
    }

    private fun showEmojis() {
        stickerMode = false
        buildEmojiTabs()
        val list = if (category < 0) recents() else supported(category)
        grid.numColumns = 8
        grid.verticalSpacing = Ui.dp(context, 2f)
        grid.adapter = EmojiAdapter(list)
        grid.setOnItemClickListener { _, v, pos, _ ->
            val e = list[pos]
            cb.onPanelFeedback(v)
            cb.onEmoji(e)
            pushRecent(e)
        }
        updateModeButtons()
    }

    private fun buildEmojiTabs() {
        tabs.removeAllViews()
        val size = Ui.dp(context, 38f)
        fun tab(content: View, selected: Boolean, onClick: () -> Unit) {
            content.background = if (selected) Ui.rounded(ThemeManager.withAlpha(theme.accent, 0.22f), Ui.dpf(context, 10f)) else null
            content.setOnClickListener { onClick() }
            tabs.addView(content, LayoutParams(size, Ui.dp(context, 34f)).apply { setMargins(Ui.dp(context, 2f), 0, Ui.dp(context, 2f), 0) })
        }
        val recentIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_kb_recent)
            Ui.tint(this, if (category < 0) theme.accent else theme.icon)
            setPadding(Ui.dp(context, 8f), Ui.dp(context, 6f), Ui.dp(context, 8f), Ui.dp(context, 6f))
        }
        tab(recentIcon, category < 0) { category = -1; showEmojis() }
        EmojiData.categories.forEachIndexed { i, (icon, _) ->
            val tv = AppCompatTextView(context).apply {
                text = icon
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                alpha = if (category == i) 1f else 0.55f
            }
            tab(tv, category == i) { category = i; showEmojis() }
        }
    }

    private inner class EmojiAdapter(private val list: List<String>) : BaseAdapter() {
        override fun getCount() = list.size
        override fun getItem(position: Int) = list[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? AppCompatTextView) ?: AppCompatTextView(context).apply {
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 46f))
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 27f)
                includeFontPadding = false
                setTextColor(theme.keyText)
            }
            tv.text = list[position]
            return tv
        }
    }

    // ------------------------------------------------------------------ stickers

    private fun showStickers() {
        stickerMode = true
        tabs.removeAllViews()
        val title = TextView(context).apply {
            text = "Stickers  ·  tap to send"
            setTextColor(theme.hintText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 12f), 0, Ui.dp(context, 12f), 0)
        }
        tabs.addView(title, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        val manage = TextView(context).apply {
            text = "+ Your photo"
            setTextColor(theme.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(Ui.dp(context, 12f), 0, Ui.dp(context, 12f), 0)
            background = Ui.rounded(ThemeManager.withAlpha(theme.accent, 0.16f), Ui.dpf(context, 14f))
            setOnClickListener { cb.onAddSticker() }
        }
        tabs.addView(manage, LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(context, 28f)))

        val files: List<File?> = listOf<File?>(null) + StickerStore.user(context) + StickerStore.builtIn(context)
        grid.numColumns = 4
        grid.verticalSpacing = Ui.dp(context, 6f)
        val adapter = StickerAdapter(files)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, v, pos, _ ->
            cb.onPanelFeedback(v)
            val f = files[pos]
            if (adapter.deletingPos == pos) {
                adapter.deletingPos = -1
                adapter.notifyDataSetChanged()
                return@setOnItemClickListener
            }
            if (f == null) cb.onAddSticker() else cb.onSticker(f)
        }
        grid.setOnItemLongClickListener { _, v, pos, _ ->
            val f = files[pos]
            if (f != null && f.parentFile?.name == "stickers") {
                adapter.deletingPos = pos
                adapter.notifyDataSetChanged()
                cb.onPanelFeedback(v)
                true
            } else false
        }
        updateModeButtons()
    }

    private inner class StickerAdapter(private val files: List<File?>) : BaseAdapter() {
        private val cell = Ui.dp(context, 78f)
        var deletingPos = -1
        override fun getCount() = files.size
        override fun getItem(position: Int) = files[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val frame = (convertView as? android.widget.FrameLayout) ?: android.widget.FrameLayout(context).apply {
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cell)
                addView(ImageView(context).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val p = Ui.dp(context, 6f)
                    setPadding(p, p, p, p)
                })
                addView(ImageView(context).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(Ui.dp(context, 28f), Ui.dp(context, 28f)).apply {
                        gravity = Gravity.TOP or Gravity.END
                        setMargins(0, Ui.dp(context, 2f), Ui.dp(context, 2f), 0)
                    }
                    setImageResource(R.drawable.ic_clear)
                    background = Ui.rounded(0xDD000000.toInt(), Ui.dpf(context, 14f))
                    Ui.tint(this, 0xFFFFFFFF.toInt())
                    val p = Ui.dp(context, 6f)
                    setPadding(p, p, p, p)
                })
            }
            val iv = frame.getChildAt(0) as ImageView
            val delBtn = frame.getChildAt(1) as ImageView
            val f = files[position]
            if (f == null) {
                val m = Ui.dp(context, 14f)
                iv.layoutParams = android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(m, m, m, m) }
                iv.setImageResource(R.drawable.ic_kb_add)
                Ui.tint(iv, theme.accent)
                iv.background = Ui.rounded(0, Ui.dpf(context, 12f), theme.accent, Ui.dp(context, 1.5f))
                iv.contentDescription = "Add your own photo sticker"
                delBtn.visibility = View.GONE
            } else {
                iv.layoutParams = android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                iv.imageTintList = null
                iv.background = null
                iv.setImageBitmap(StickerStore.thumbnail(f, cell))
                iv.contentDescription = "Sticker"
                if (deletingPos == position) {
                    delBtn.visibility = View.VISIBLE
                    delBtn.setOnClickListener {
                        StickerStore.delete(f)
                        showStickers()
                    }
                } else {
                    delBtn.visibility = View.GONE
                    delBtn.setOnClickListener(null)
                }
            }
            return frame
        }
    }

    // ------------------------------------------------------------------ bottom bar

    private fun bottomBar(): View {
        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 6f), Ui.dp(context, 4f), Ui.dp(context, 6f), Ui.dp(context, 4f))
        }
        val abc = TextView(context).apply {
            text = "ABC"
            setTextColor(theme.keyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            background = Ui.keyBackground(context, theme, theme.specialKeyBg)
            setOnClickListener { cb.onBackToKeyboard() }
        }
        bar.addView(abc, LayoutParams(Ui.dp(context, 64f), LayoutParams.MATCH_PARENT))
        bar.addView(View(context), LayoutParams(0, 1, 1f))

        btnEmojiMode = AppCompatTextView(context).apply {
            text = "🙂"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setOnClickListener { if (stickerMode) showEmojis() }
            contentDescription = "Emoji"
        }
        btnStickerMode = ImageView(context).apply {
            setImageResource(R.drawable.ic_kb_sticker)
            val p = Ui.dp(context, 7f)
            setPadding(p, p, p, p)
            setOnClickListener { if (!stickerMode) showStickers() }
            contentDescription = "Stickers"
        }
        bar.addView(btnEmojiMode, LayoutParams(Ui.dp(context, 52f), LayoutParams.MATCH_PARENT))
        bar.addView(btnStickerMode, LayoutParams(Ui.dp(context, 52f), LayoutParams.MATCH_PARENT).apply { marginStart = Ui.dp(context, 6f) })
        bar.addView(View(context), LayoutParams(0, 1, 1f))

        val del = ImageView(context).apply {
            setImageResource(R.drawable.ic_kb_backspace)
            Ui.tint(this, theme.icon)
            val p = Ui.dp(context, 8f)
            setPadding(p, p, p, p)
            background = Ui.keyBackground(context, theme, theme.specialKeyBg)
            contentDescription = "Delete"
        }
        attachRepeatDelete(del)
        bar.addView(del, LayoutParams(Ui.dp(context, 64f), LayoutParams.MATCH_PARENT))
        return bar
    }

    private fun updateModeButtons() {
        if (!::btnEmojiMode.isInitialized) return
        val sel = Ui.rounded(ThemeManager.withAlpha(theme.accent, 0.22f), Ui.dpf(context, 16f))
        btnEmojiMode.background = if (!stickerMode) sel else null
        btnEmojiMode.alpha = if (!stickerMode) 1f else 0.6f
        btnStickerMode.background = if (stickerMode) sel else null
        Ui.tint(btnStickerMode, if (stickerMode) theme.accent else theme.icon)
    }

    /** Same hold-to-repeat behaviour as the keyboard's delete key. */
    private fun attachRepeatDelete(v: View) {
        var count = 0
        val r = object : Runnable {
            override fun run() {
                count++
                cb.onPanelDelete(count > 20)
                v.postDelayed(this, if (count > 20) 140L else 55L)
            }
        }
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    count = 0
                    cb.onPanelFeedback(view)
                    cb.onPanelDelete(false)
                    view.postDelayed(r, 400L)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    view.removeCallbacks(r)
                }
            }
            true
        }
    }

    companion object {
        private const val PREF_RECENT = "KB_RECENT_EMOJI"
    }
}
