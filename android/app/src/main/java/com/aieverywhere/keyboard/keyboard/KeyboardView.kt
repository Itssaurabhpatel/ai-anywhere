package com.aieverywhere.keyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.aieverywhere.keyboard.R
import kotlin.math.abs

interface KeyboardActionListener {
    fun onKeyText(text: String)
    fun onKeyDelete(wholeWord: Boolean)
    fun onKeyEnter()
    fun onKeyEmoji()
    fun onCursorMove(steps: Int)
    /** Shift pressed: return true if a text selection was re-cased (then the shift state is left alone). */
    fun onShiftWithSelection(): Boolean
    fun onSpaceLongPress()
    /** Globe/language key tapped — cycle to next language. */
    fun onLanguageKey()
    fun onGifToggle()
    fun onKeyFeedback(view: View, type: KeyType)
}

class SwipeTrailView(context: Context) : View(context) {
    // Trail is stored as a list of points so we can draw a tapered gradient stroke
    private val points = mutableListOf<android.graphics.PointF>()
    // Accent color set by the keyboard theme
    var trailColor: Int = 0xFF007AFF.toInt()
    private var fadeAlpha = 255
    private val fadeHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var fadeRunnable: Runnable? = null

    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeJoin = android.graphics.Paint.Join.ROUND
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    fun addPoint(x: Float, y: Float) {
        cancelFade()
        fadeAlpha = 255
        points.add(android.graphics.PointF(x, y))
        invalidate()
    }

    fun clear() {
        cancelFade()
        points.clear()
        fadeAlpha = 255
        invalidate()
    }

    /** Start a smooth fade-out after the finger lifts, like Gboard. */
    fun fadeOut() {
        cancelFade()
        scheduleFade()
    }

    private fun cancelFade() {
        fadeRunnable?.let { fadeHandler.removeCallbacks(it) }
        fadeRunnable = null
    }

    private fun scheduleFade() {
        val r = object : Runnable {
            override fun run() {
                fadeAlpha -= 40  // ~6 steps of 16ms → ~100ms total
                if (fadeAlpha <= 0) {
                    fadeAlpha = 0
                    points.clear()
                    invalidate()
                } else {
                    invalidate()
                    fadeHandler.postDelayed(this, 16)
                }
            }
        }
        fadeRunnable = r
        fadeHandler.postDelayed(r, 50)  // 50ms pause then start fading
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) return
        val maxWidth = 18f   // px at fingertip end
        val minWidth = 4f    // px at tail end
        val total = points.size
        for (i in 1 until total) {
            val t = i.toFloat() / total   // 0 = tail, 1 = fingertip
            val strokeW = minWidth + (maxWidth - minWidth) * t
            // Alpha: base fadeAlpha * t so tail is more transparent
            val alpha = ((fadeAlpha * (0.25f + 0.75f * t)).toInt()).coerceIn(0, 255)
            paint.strokeWidth = strokeW
            paint.color = trailColor
            paint.alpha = alpha
            canvas.drawLine(points[i - 1].x, points[i - 1].y, points[i].x, points[i].y, paint)
        }
    }
}

enum class ShiftState { OFF, ON, LOCKED }

/**
 * GBoard-style keyboard built in code, so every page (letters, ?123, =\<, number pad) shares
 * the same sizing, theme and touch behaviour.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class KeyboardView(
    context: Context,
    private val theme: KeyboardTheme,
    private val listener: KeyboardActionListener
) : LinearLayout(context) {

    var page: Page = Page.LETTERS
        private set
    private var basePage = Page.LETTERS
    private var fieldKind = FieldKind.TEXT
    var numberRow = false
    var showLanguageKey = false  // When true, shows globe key instead of emoji in bottom row
    var popupEnabled = true

    var shift = ShiftState.OFF
        private set
    private var lastShiftTapAt = 0L
    private var enterIconRes = R.drawable.ic_kb_enter

    private val keys = mutableListOf<KeyView>()
    private val handler = Handler(Looper.getMainLooper())
    private val preview = KeyPreview()
    private val altsPopup = KeyAltsPopup()
    private val hGap = Ui.dp(context, 4f)
    private val vGap = Ui.dp(context, 8f)
    private val keyRects = mutableMapOf<KeyView, android.graphics.RectF>()
    private var swipeHighlightedKey: KeyView? = null
    private var isSwiping = false
    private val swipeKeys = mutableListOf<KeyView>()
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    var trailView: SwipeTrailView? = null

    init {
        orientation = VERTICAL
    }
    // ------------------------------------------------------------------ key alts balloon

    private inner class KeyAltsPopup {
        private var popup: PopupWindow? = null
        private var container: LinearLayout? = null
        private var activeAltIndex = -1
        private var altsStr = ""
        private var anchorKey: KeyView? = null
        private var charViews = mutableListOf<TextView>()
        private var popupX = 0

        fun show(anchor: KeyView, alts: String) {
            if (!anchor.isAttachedToWindow || anchor.windowToken == null) return
            try {
                this.altsStr = alts
                this.anchorKey = anchor
                this.activeAltIndex = 0
                this.charViews.clear()

                val ll = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    background = Ui.rounded(theme.previewBg, Ui.dpf(context, 8f))
                    setPadding(
                        Ui.dp(context, 4f), Ui.dp(context, 4f),
                        Ui.dp(context, 4f), Ui.dp(context, 4f)
                    )
                }

                val charW = (anchor.width * 0.9f).toInt()
                val charH = (anchor.height * 0.95f).toInt()
                val cellMargin = 0

                for ((i, c) in alts.withIndex()) {
                    val tv = TextView(context).apply {
                        gravity = Gravity.CENTER
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                        typeface = android.graphics.Typeface.create(
                            "sans-serif", android.graphics.Typeface.NORMAL
                        )
                        text = c.toString()
                        val lp = LinearLayout.LayoutParams(charW, charH)
                        lp.setMargins(cellMargin, 0, cellMargin, 0)
                        layoutParams = lp
                        if (i == 0) {
                            background = Ui.rounded(theme.accent, Ui.dpf(context, 6f))
                            setTextColor(theme.accentText)
                        } else {
                            background = null
                            setTextColor(theme.keyText)
                        }
                    }
                    this.charViews.add(tv)
                    ll.addView(tv)
                }

                this.container = ll
                ll.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val totalW = ll.measuredWidth
                val totalH = ll.measuredHeight

                val loc = IntArray(2)
                anchor.getLocationInWindow(loc)
                val screenWidth = context.resources.displayMetrics.widthPixels
                
                var px = loc[0] + anchor.width / 2 - totalW / 2
                px = px.coerceIn(0, screenWidth - totalW)
                
                val py = loc[1] - totalH - Ui.dp(context, 10f)
                this.popupX = px

                val p = PopupWindow(ll, totalW, totalH, false)
                p.elevation = Ui.dp(context, 8f).toFloat()
                p.isTouchable = false
                p.animationStyle = android.R.style.Animation_Toast
                p.showAtLocation(anchor, Gravity.NO_GRAVITY, px, py)
                popup = p
            } catch (e: Exception) {}
        }

        fun handleMove(x: Float) {
            if (popup?.isShowing != true || anchorKey == null) return
            val anchor = anchorKey!!
            val charW = anchor.width
            
            val loc = IntArray(2)
            anchor.getLocationInWindow(loc)
            val touchScreenX = loc[0] + x
            
            val startX = popupX + Ui.dp(context, 4f).toFloat()
            var index = ((touchScreenX - startX) / charW).toInt()
            index = index.coerceIn(0, altsStr.length - 1)

            if (index != activeAltIndex) {
                charViews.getOrNull(activeAltIndex)?.apply {
                    background = null
                    setTextColor(theme.keyText)
                }
                activeAltIndex = index
                charViews.getOrNull(activeAltIndex)?.apply {
                    background = Ui.rounded(theme.accent, Ui.dpf(context, 6f))
                    setTextColor(theme.accentText)
                }
            }
        }

        fun getActiveChar(): String? {
            return if (activeAltIndex in altsStr.indices) altsStr[activeAltIndex].toString() else null
        }

        fun isShowing() = popup?.isShowing == true

        fun hide() {
            try {
                popup?.dismiss()
            } catch (e: Exception) {}
        }
    }

    fun dismissPopups() {
        preview.hide()
        altsPopup.hide()
        keys.forEach { cancelPending(it) }
    }

    // ------------------------------------------------------------------ building

    private fun build() {
        preview.hide()
        altsPopup.hide()
        removeAllViews()
        keys.clear()

        if (page == Page.NUMBER) {
            buildNumberPageWithSidebar()
            refreshShift()
            return
        }

        val rows = KeyboardLayouts.rows(page, fieldKind, numberRow && page == Page.LETTERS, showLanguageKey && page == Page.LETTERS)
        for (row in rows) {
            val rowLayout = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
                isMotionEventSplittingEnabled = true
            }
            for (def in row) {
                if (def.type == KeyType.SPACER) {
                    rowLayout.addView(View(context), LayoutParams(0, LayoutParams.MATCH_PARENT, def.weight))
                    continue
                }
                val kv = createKey(def)
                val lp = LayoutParams(0, LayoutParams.MATCH_PARENT, def.weight)
                // No margins: the gap is drawn by the inset background so taps in the gap still hit a key.
                rowLayout.addView(kv, lp)
                keys += kv
            }
            addView(rowLayout)
        }
        refreshShift()
    }

    private fun buildNumberPageWithSidebar() {
        val topSection = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 3f)
            isMotionEventSplittingEnabled = true
        }

        val sidebarScroll = android.widget.ScrollView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            isVerticalScrollBarEnabled = false
            isScrollbarFadingEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        }
        val sidebarInner = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        
        val sidebarDefs = listOf(
            KeyDef("+", special = true),
            KeyDef("-", special = true),
            KeyDef("*", special = true),
            KeyDef("/", special = true),
            KeyDef("(", special = true),
            KeyDef(")", special = true)
        )
        val sidebarKeys = mutableListOf<KeyView>()
        for (def in sidebarDefs) {
            val kv = createKey(def)
            sidebarInner.addView(kv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            keys += kv
            sidebarKeys += kv
        }
        sidebarScroll.addView(sidebarInner)
        topSection.addView(sidebarScroll)
        
        val mainGrid = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 4f)
        }
        val rows = listOf(
            listOf(KeyDef("1"), KeyDef("2"), KeyDef("3"), KeyDef("%", special = true)),
            listOf(KeyDef("4"), KeyDef("5"), KeyDef("6"), KeyDef("_", special = true)),
            listOf(KeyDef("7"), KeyDef("8"), KeyDef("9"), KeyDef("", KeyType.DELETE, special = true))
        )
        for (row in rows) {
            val rowLayout = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            }
            for (def in row) {
                val kv = createKey(def)
                rowLayout.addView(kv, LayoutParams(0, LayoutParams.MATCH_PARENT, def.weight))
                keys += kv
            }
            mainGrid.addView(rowLayout)
        }
        topSection.addView(mainGrid)
        addView(topSection)

        // Equalize sidebar keys height to exactly match the row height of the main numeric grid
        mainGrid.post {
            val gridHeight = mainGrid.height
            if (gridHeight > 0 && rows.isNotEmpty()) {
                val singleRowHeight = gridHeight / rows.size
                for (k in sidebarKeys) {
                    val lp = k.layoutParams
                    if (lp.height != singleRowHeight) {
                        lp.height = singleRowHeight
                        k.layoutParams = lp
                    }
                }
            }
        }
        
        val row4 = listOf(
            KeyDef("ABC", KeyType.TO_BASE, special = true), 
            KeyDef(",", special = true, weight = 0.5f), 
            KeyDef("!?#", KeyType.TO_SYMBOLS, special = true, weight = 0.5f), 
            KeyDef("0"), 
            KeyDef("=", special = true, weight = 0.5f), 
            KeyDef(".", special = true, weight = 0.5f), 
            KeyDef("", KeyType.ENTER, special = true)
        )
        val row4Layout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        for (def in row4) {
            val kv = createKey(def)
            row4Layout.addView(kv, LayoutParams(0, LayoutParams.MATCH_PARENT, def.weight))
            keys += kv
        }
        addView(row4Layout)
    }

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private fun tinted(res: Int, color: Int): Drawable? =
        context.getDrawable(res)?.mutate()?.apply { setTint(color) }

    private fun createKey(def: KeyDef): KeyView {
        val kv = KeyView(context, def)
        val isPad = page == Page.NUMBER || page == Page.PHONE
        kv.textColor = theme.keyText
        kv.hintColor = theme.hintText
        kv.textSizePx = when {
            isPad && def.type == KeyType.CHAR && !def.special -> sp(26f)
            def.type == KeyType.CHAR && def.label.length == 1 && def.label[0].isDigit() -> sp(24f)  // Top row numbers bigger
            def.type == KeyType.CHAR && def.label.length == 1 && def.label[0].isLetter() -> sp(24f)
            def.type == KeyType.CHAR -> sp(18f)
            else -> sp(13f)
        }
        kv.iconSizePx = Ui.dp(context, 20f)  // Gboard icon size
        val bgColor = when {
            def.type == KeyType.ENTER -> theme.accent
            def.special -> theme.specialKeyBg
            else -> theme.keyBg
        }
        val ix = hGap / 2
        val iy = vGap / 2
        kv.background = android.graphics.drawable.InsetDrawable(
            Ui.keyBackground(context, theme, bgColor, radiusDp = 5f), ix, iy, ix, iy)  // Gboard: ~5dp radius
        kv.setPadding(ix, iy, ix, iy)
        kv.elevation = 0f
        when (def.type) {
            KeyType.DELETE -> kv.icon = tinted(R.drawable.ic_kb_backspace, theme.icon)
            KeyType.EMOJI -> kv.icon = tinted(R.drawable.ic_kb_emoji, theme.icon)
            KeyType.LANGUAGE -> kv.icon = tinted(R.drawable.ic_kb_language, theme.icon)  // Globe icon
            KeyType.GIF -> kv.icon = tinted(R.drawable.ic_kb_sticker, theme.icon)  // Sticker/GIF icon
            KeyType.ENTER -> kv.icon = tinted(enterIconRes, theme.accentText)
            KeyType.SPACE -> {
                kv.label = ""
                if (!isPad) kv.caption = null  // Gboard space bar is blank
                kv.textSizePx = sp(20f)
            }
            else -> {}
        }
        kv.contentDescription = when (def.type) {
            KeyType.SHIFT -> "Shift"
            KeyType.DELETE -> "Delete"
            KeyType.SPACE -> "Space"
            KeyType.ENTER -> "Enter"
            KeyType.EMOJI -> "Emoji"
            KeyType.LANGUAGE -> "Switch language"
            KeyType.GIF -> "Stickers"
            KeyType.TO_NUMBER -> "Numeric pad"
            else -> def.label
        }
        kv.setOnTouchListener { v, e -> onKeyTouch(v as KeyView, e); true }
        return kv
    }

    private fun refreshShift() {
        for (k in keys) {
            if (k.def.type == KeyType.CHAR && page == Page.LETTERS && k.def.label.length == 1 && k.def.label[0].isLetter()) {
                k.label = if (shift == ShiftState.OFF) k.def.label.lowercase() else k.def.label.uppercase()
            }
            if (k.def.type == KeyType.SHIFT) {
                val (res, color) = when (shift) {
                    ShiftState.OFF -> R.drawable.ic_kb_shift to theme.icon
                    ShiftState.ON -> R.drawable.ic_kb_shift_on to theme.accent
                    ShiftState.LOCKED -> R.drawable.ic_kb_shift_lock to theme.accent
                }
                k.icon = tinted(res, color)
            }
        }
    }

    // ------------------------------------------------------------------ touch handling

    private fun cancelPending(k: KeyView) {
        k.pending?.let { handler.removeCallbacks(it) }
        k.pending = null
    }

    private fun schedule(k: KeyView, delay: Long, r: Runnable) {
        cancelPending(k)
        k.pending = r
        handler.postDelayed(r, delay)
    }

    /** Build a flat map of key → screen-relative RectF for fast hit-testing during swipe. */
    private fun buildKeyRects() {
        keyRects.clear()
        val myLoc = IntArray(2)
        this.getLocationInWindow(myLoc)
        for (k in keys) {
            val loc = IntArray(2)
            k.getLocationInWindow(loc)
            val left = (loc[0] - myLoc[0]).toFloat()
            val top  = (loc[1] - myLoc[1]).toFloat()
            keyRects[k] = android.graphics.RectF(left, top, left + k.width, top + k.height)
        }
    }

    private fun findKeyAt(x: Float, y: Float): KeyView? {
        for ((k, r) in keyRects) {
            if (r.contains(x, y)) return k
        }
        return null
    }

    /** Highlight a key as "currently under finger" during swipe, un-highlight the previous one. */
    private fun setSwipeHighlight(key: KeyView?) {
        if (key == swipeHighlightedKey) return
        swipeHighlightedKey?.isPressed = false
        swipeHighlightedKey = key
        key?.isPressed = true
    }

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        val x = e.x
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isSwiping = false
                swipeKeys.clear()
                swipeStartX = x
                swipeStartY = y
                trailView?.clear()
                // Build key rects once per touch — positions only change on layout/rebuild
                buildKeyRects()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - swipeStartX
                val dy = y - swipeStartY
                val swipeEnabled = page == Page.LETTERS && com.aieverywhere.keyboard.keyboard.ThemeManager.prefs(context).getBoolean("SWIPE_TYPING", true)
                val thr = Ui.dpf(context, 24f)
                if (swipeEnabled && !isSwiping && e.pointerCount == 1 && !altsPopup.isShowing() &&
                    (dx * dx + dy * dy) > thr * thr &&
                    findKeyAt(x, y).let { it != null && it != findKeyAt(swipeStartX, swipeStartY) }) {
                    isSwiping = true
                    // Cancel all pending key repeats / long-presses
                    for (k in keys) {
                        k.isPressed = false
                        cancelPending(k)
                    }
                    if (popupEnabled) preview.hide()
                    altsPopup.hide()

                    val cancel = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()

                    // Add the start point to the trail
                    trailView?.addPoint(swipeStartX, swipeStartY)
                }
                if (isSwiping) {
                    trailView?.addPoint(x, y)

                    val key = findKeyAt(x, y)
                    // Only letter keys count as swipe stops
                    if (key != null && key.def.type == KeyType.CHAR &&
                        key.def.label.length == 1 && key.def.label[0].isLetter()) {
                        if (swipeKeys.isEmpty() || swipeKeys.last() != key) {
                            swipeKeys.add(key)
                            listener.onKeyFeedback(key, KeyType.CHAR)
                        }
                    }
                    setSwipeHighlight(key)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSwiping) {
                    setSwipeHighlight(null)
                    trailView?.fadeOut()  // Gboard-style fade-out animation
                    isSwiping = false
                    if (e.actionMasked == MotionEvent.ACTION_UP) {
                        if (swipeKeys.size == 1) {
                            // Single-key swipe = tap the key (Gboard behaviour)
                            val k = swipeKeys.first()
                            listener.onKeyText(k.label)
                            if (shift == ShiftState.ON) {
                                shift = ShiftState.OFF
                                refreshShift()
                            }
                        } else if (swipeKeys.size > 1) {
                            val deduplicated = buildDeduplicatedPath(swipeKeys)
                            if (deduplicated.isNotEmpty()) {
                                val matchedWord = matchSwipe(deduplicated, swipeKeys)
                                listener.onKeyText("$matchedWord ")
                            }
                        }
                    }
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(e)
    }

    /** Remove consecutive duplicate letters from the swipe path. */
    private fun buildDeduplicatedPath(keyList: List<KeyView>): String {
        val sb = StringBuilder()
        var last = ' '
        for (k in keyList) {
            val c = k.label.firstOrNull()?.lowercaseChar() ?: continue
            if (c != last) { sb.append(c); last = c }
        }
        return sb.toString()
    }

    /**
     * Gboard-style swipe matching:
     * 1. Must start with same letter.
     * 2. Score each candidate by how closely its de-duplicated key sequence matches the swiped
     *    sequence, weighted by path length similarity (avoids matching very short words to long swipes).
     * 3. Pick the highest-scoring word; fall back to the raw swipe string if nothing matches well.
     */
    private fun matchSwipe(swiped: String, swipeKeyList: List<KeyView>): String {
        if (swiped.length < 2) return swiped

        data class Candidate(val word: String, val score: Float)
        val candidates = mutableListOf<Candidate>()

        for (word in SwipeDictionary.words) {
            if (word.isEmpty()) continue
            if (word.first() != swiped.first()) continue  // must start same letter

            val reduced = word.filterIndexed { i, c -> i == 0 || c != word[i - 1] }

            // The word's reduced key-sequence must be a subsequence of the swipe path
            if (!isSubsequence(reduced, swiped)) continue

            // Score 1: how close are the lengths? Penalise words much shorter/longer than the swipe
            val lengthRatio = minOf(reduced.length, swiped.length).toFloat() /
                              maxOf(reduced.length, swiped.length)

            // Score 2: does the word end with the same letter? Big bonus
            val endBonus = if (word.last() == swiped.last()) 0.5f else 0f

            // Score 3: prefer words whose length is close to the swipe key count
            val keyCountDiff = kotlin.math.abs(word.length - swipeKeyList.size).toFloat()
            val keyCountScore = 1f / (1f + keyCountDiff * 0.3f)

            val score = lengthRatio * 0.4f + endBonus + keyCountScore * 0.3f +
                        (word.length.toFloat() / 20f).coerceAtMost(0.2f)  // slight preference for longer words
            candidates.add(Candidate(word, score))
        }

        return candidates.maxByOrNull { it.score }?.word ?: swiped
    }

    private fun isSubsequence(query: String, text: String): Boolean {
        var qIdx = 0
        var tIdx = 0
        while (qIdx < query.length && tIdx < text.length) {
            if (query[qIdx] == text[tIdx]) qIdx++
            tIdx++
        }
        return qIdx == query.length
    }

    private fun onKeyTouch(k: KeyView, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                k.isPressed = true
                k.longFired = false
                k.swiping = false
                k.downX = e.x
                k.lastX = e.x
                k.repeatCount = 0
                listener.onKeyFeedback(k, k.def.type)
                when (k.def.type) {
                    KeyType.CHAR -> {
                        if (popupEnabled && page != Page.NUMBER && page != Page.PHONE) preview.show(k, k.label)
                        val alts = k.def.alts
                        val h = k.hint
                        if (alts.isNotEmpty()) {
                            schedule(k, LONG_PRESS_MS, Runnable {
                                k.longFired = true
                                preview.hide()
                                if (popupEnabled) altsPopup.show(k, k.label + alts)
                                listener.onKeyFeedback(k, KeyType.CHAR)
                            })
                        } else if (!h.isNullOrEmpty()) {
                            schedule(k, LONG_PRESS_MS, Runnable {
                                k.longFired = true
                                if (popupEnabled) preview.show(k, h)
                                listener.onKeyFeedback(k, KeyType.CHAR)
                                listener.onKeyText(h)
                            })
                        }
                    }
                    KeyType.DELETE -> {
                        listener.onKeyDelete(false)
                        schedule(k, 400L, deleteRepeater(k))
                    }
                    KeyType.SPACE -> schedule(k, 550L, Runnable {
                        if (!k.swiping) {
                            k.longFired = true
                            listener.onSpaceLongPress()
                        }
                    })
                    else -> {}
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (altsPopup.isShowing()) {
                    altsPopup.handleMove(e.x)
                } else if (k.def.type == KeyType.SPACE) {
                    val step = Ui.dp(context, 11f).toFloat()
                    if (!k.swiping && abs(e.x - k.downX) > Ui.dp(context, 16f)) {
                        k.swiping = true
                        cancelPending(k)
                        k.lastX = e.x
                    }
                    if (k.swiping) {
                        val steps = ((e.x - k.lastX) / step).toInt()
                        if (steps != 0) {
                            listener.onCursorMove(steps)
                            k.lastX += steps * step
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                k.isPressed = false
                cancelPending(k)
                preview.hide()
                val selectedAlt = if (altsPopup.isShowing()) altsPopup.getActiveChar() else null
                altsPopup.hide()
                if (selectedAlt != null) {
                    listener.onKeyText(selectedAlt)
                    if (shift == ShiftState.ON) { shift = ShiftState.OFF; refreshShift() }
                } else if (!k.longFired && !k.swiping && k.def.type != KeyType.DELETE) {
                    onKeyTap(k)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                k.isPressed = false
                cancelPending(k)
                preview.hide()
                altsPopup.hide()
            }
        }
    }

    /** Hold delete: repeats characters, then speeds up to whole words (like GBoard). */
    private fun deleteRepeater(k: KeyView): Runnable = object : Runnable {
        override fun run() {
            k.repeatCount++
            val words = k.repeatCount > 20
            listener.onKeyDelete(words)
            if (k.repeatCount % 2 == 0) listener.onKeyFeedback(k, KeyType.DELETE)
            k.pending = this
            handler.postDelayed(this, if (words) 140L else 55L)
        }
    }

    private fun onKeyTap(k: KeyView) {
        when (k.def.type) {
            KeyType.CHAR -> {
                listener.onKeyText(k.label)
                if (shift == ShiftState.ON) {
                    shift = ShiftState.OFF
                    refreshShift()
                }
            }
            KeyType.SPACE -> listener.onKeyText(" ")
            KeyType.ENTER -> listener.onKeyEnter()
            KeyType.EMOJI -> listener.onKeyEmoji()
            KeyType.GIF -> listener.onGifToggle()
            KeyType.LANGUAGE -> listener.onLanguageKey()  // Globe key → cycle language
            KeyType.SHIFT -> onShiftTap()
            KeyType.TO_SYMBOLS -> setPage(Page.SYMBOLS)
            KeyType.TO_MORE -> setPage(Page.MORE_SYMBOLS)
            KeyType.TO_BASE -> setPage(basePage)
            KeyType.TO_NUMBER -> setPage(Page.NUMBER)
            else -> {}
        }
    }

    fun configure(kind: FieldKind) {
        val changed = fieldKind != kind
        fieldKind = kind
        if (keys.isEmpty() || changed) build()
    }

    fun setEnterIcon(resId: Int) {
        if (enterIconRes == resId) return
        enterIconRes = resId
        refreshShift() // Actually we should refresh the enter key, but build() or a targeted refresh works. Let's just do a targeted refresh if possible, or we can rebuild.
        // Actually, we can just find the enter key and update it.
        for (k in keys) {
            if (k.def.type == KeyType.ENTER) {
                k.icon = tinted(resId, theme.accentText)
            }
        }
    }

    fun setAutoShift(auto: Boolean) {
        if (shift == ShiftState.LOCKED) return
        val newShift = if (auto) ShiftState.ON else ShiftState.OFF
        if (shift != newShift) {
            shift = newShift
            refreshShift()
        }
    }

    fun setPage(newPage: Page) {
        if (page == newPage) return
        page = newPage
        build()
    }

    private fun onShiftTap() {
        if (listener.onShiftWithSelection()) return
        val now = SystemClock.uptimeMillis()
        val quick = now - lastShiftTapAt < DOUBLE_TAP_MS
        shift = when {
            shift == ShiftState.LOCKED -> ShiftState.OFF
            quick -> ShiftState.LOCKED            // double tap = caps lock (also from auto-caps)
            shift == ShiftState.ON -> ShiftState.OFF
            else -> ShiftState.ON
        }
        lastShiftTapAt = if (shift == ShiftState.LOCKED) 0L else now
        refreshShift()
    }

    // ------------------------------------------------------------------ key preview balloon

    private inner class KeyPreview {
        private var popup: PopupWindow? = null
        private var text: TextView? = null

        fun show(anchor: KeyView, label: String) {
            if (!anchor.isAttachedToWindow || anchor.windowToken == null) return
            try {
                val tv = text ?: TextView(context).apply {
                    gravity = Gravity.CENTER
                    setTextColor(theme.keyText)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                    background = Ui.rounded(theme.previewBg, Ui.dpf(context, 8f),
                        if (theme.isDark) 0x40FFFFFF else 0x26000000, Ui.dp(context, 1f))
                    elevation = Ui.dpf(context, 4f)
                }.also { text = it }
                tv.text = label
                val w = maxOf((anchor.width * 1.05f).toInt(), Ui.dp(context, 36f))
                val h = (anchor.height * 1.15f).toInt()
                val loc = IntArray(2)
                anchor.getLocationInWindow(loc)
                val x = loc[0] + anchor.width / 2 - w / 2
                val y = loc[1] - h - Ui.dp(context, 2f)  // 2dp gap between key top and balloon bottom
                val p = popup ?: PopupWindow(tv, w, h, false).apply {
                    isTouchable = false
                    isClippingEnabled = false
                    animationStyle = 0
                }.also { popup = it }
                if (p.isShowing) p.update(x, y, w, h) else p.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
            } catch (e: Exception) {
                // Window not ready (e.g. while the keyboard animates in) - skip the balloon.
            }
        }

        fun hide() {
            try {
                popup?.dismiss()
            } catch (e: Exception) {
            }
        }
    }

    companion object {
        private const val LONG_PRESS_MS = 280L
        private const val DOUBLE_TAP_MS = 350L
    }
}





