package com.aieverywhere.keyboard.keyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.widget.EditText
import java.text.BreakIterator

/** Where typed text goes: the app's field, or one of the keyboard's own boxes (AI prompt / translate). */
interface InputTarget {
    fun commitText(text: CharSequence)
    fun deleteBackward()
    fun deleteWordBackward()
    fun selectedText(): String?
    /** Replaces the current selection and keeps the new text selected (used by Shift on a selection). */
    fun replaceSelectionKeepSelected(newText: String)
    fun textBeforeCursor(n: Int): String
    fun moveCursor(steps: Int)
    fun deleteSurroundingText(before: Int, after: Int = 0)
}

/** Length to delete for "delete previous word" (trailing spaces + the word before them). */
internal fun wordDeleteLength(before: String): Int {
    if (before.isEmpty()) return 0
    var i = before.length
    while (i > 0 && before[i - 1].isWhitespace()) i--
    if (i > 0 && !before[i - 1].isLetterOrDigit()) return before.length - i + 1
    while (i > 0 && before[i - 1].isLetterOrDigit()) i--
    return (before.length - i).coerceAtLeast(1)
}

class IcTarget(private val svc: InputMethodService) : InputTarget {
    private val ic get() = svc.currentInputConnection

    override fun commitText(text: CharSequence) {
        ic?.commitText(text, 1)
    }

    override fun deleteBackward() {
        val conn = ic ?: return
        val sel = conn.getSelectedText(0)
        if (!sel.isNullOrEmpty()) {
            conn.commitText("", 1)
        } else {
            // A real DEL key event lets the app remove whole emoji / grapheme clusters correctly.
            svc.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    override fun deleteWordBackward() {
        val conn = ic ?: return
        val sel = conn.getSelectedText(0)
        if (!sel.isNullOrEmpty()) {
            conn.commitText("", 1); return
        }
        val len = wordDeleteLength(conn.getTextBeforeCursor(64, 0)?.toString() ?: "")
        if (len > 0) conn.deleteSurroundingText(len, 0) else svc.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    override fun selectedText(): String? = ic?.getSelectedText(0)?.toString()

    override fun replaceSelectionKeepSelected(newText: String) {
        val conn = ic ?: return
        val et = conn.getExtractedText(ExtractedTextRequest(), 0)
        conn.beginBatchEdit()
        conn.commitText(newText, 1)
        if (et != null && et.selectionStart >= 0) {
            val start = et.startOffset + minOf(et.selectionStart, et.selectionEnd)
            conn.setSelection(start, start + newText.length)
        }
        conn.endBatchEdit()
    }

    override fun textBeforeCursor(n: Int): String = ic?.getTextBeforeCursor(n, 0)?.toString() ?: ""

    override fun moveCursor(steps: Int) {
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) { svc.sendDownUpKeyEvents(code) }
    }

    override fun deleteSurroundingText(before: Int, after: Int) {
        ic?.deleteSurroundingText(before, after)
    }
}

class EditTextTarget(private val et: EditText) : InputTarget {
    private fun editable(): android.text.Editable {
        val e = et.text
        if (e == null) {
            val builder = android.text.SpannableStringBuilder("")
            et.setText(builder, android.widget.TextView.BufferType.EDITABLE)
            return builder
        }
        return e
    }

    private fun range(): Pair<Int, Int> {
        val e = editable()
        var s = et.selectionStart
        var selEnd = et.selectionEnd
        if (s < 0) s = e.length
        if (selEnd < 0) selEnd = e.length
        return Pair(minOf(s, selEnd), maxOf(s, selEnd))
    }

    override fun commitText(text: CharSequence) {
        val e = editable()
        val (s, selEnd) = range()
        e.replace(s, selEnd, text)
        et.setSelection((s + text.length).coerceAtMost(e.length))
    }

    override fun deleteBackward() {
        val e = editable()
        val (s, selEnd) = range()
        if (s != selEnd) {
            e.delete(s, selEnd)
            et.setSelection(s.coerceAtMost(e.length))
            return
        }
        if (s == 0) return
        val bi = BreakIterator.getCharacterInstance()
        bi.setText(e.toString())
        val prev = bi.preceding(s).let { if (it == BreakIterator.DONE) s - 1 else it }
        e.delete(prev, s)
        et.setSelection(prev.coerceAtMost(e.length))
    }

    override fun deleteWordBackward() {
        val e = editable()
        val (s, selEnd) = range()
        if (s != selEnd) {
            e.delete(s, selEnd)
            et.setSelection(s.coerceAtMost(e.length))
            return
        }
        val len = wordDeleteLength(e.substring(0, s))
        if (len > 0) {
            e.delete(s - len, s)
            et.setSelection((s - len).coerceAtMost(e.length))
        }
    }

    override fun selectedText(): String? {
        val e = editable()
        val (s, selEnd) = range()
        return if (s == selEnd) null else e.substring(s, selEnd)
    }

    override fun replaceSelectionKeepSelected(newText: String) {
        val e = editable()
        val (s, selEnd) = range()
        e.replace(s, selEnd, newText)
        et.setSelection(s, (s + newText.length).coerceAtMost(e.length))
    }

    override fun textBeforeCursor(n: Int): String {
        val e = editable()
        val (s, _) = range()
        return e.substring(maxOf(0, s - n), s)
    }

    override fun moveCursor(steps: Int) {
        val e = editable()
        val (s, _) = range()
        et.setSelection((s + steps).coerceIn(0, e.length))
    }

    override fun deleteSurroundingText(before: Int, after: Int) {
        val e = editable()
        val (s, selEnd) = range()
        val delStart = (s - before).coerceAtLeast(0)
        val delEnd = (selEnd + after).coerceAtMost(e.length)
        if (delStart < delEnd) {
            e.delete(delStart, delEnd)
            et.setSelection(delStart.coerceAtMost(e.length))
        }
    }
}
