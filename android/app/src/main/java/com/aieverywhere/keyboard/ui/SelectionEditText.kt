package com.aieverywhere.keyboard.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import com.aieverywhere.keyboard.keyboard.Ui

class SelectionEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    var onSelectionChangeListener: ((hasSelection: Boolean, start: Int, end: Int) -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangeListener?.invoke(selStart != selEnd, selStart, selEnd)
    }
}

/**
 * Floating text selection toolbar floating above the selected text,
 * styled exactly like native Android ActionMode menu (Cut | Copy | Paste | Select All).
 */
class FloatingSelectionToolbar(
    private val editText: SelectionEditText,
    private val isEditable: () -> Boolean = { true },
    private val onCopy: (String) -> Unit,
    private val onCut: (String) -> Unit,
    private val onPaste: () -> Unit,
    private val onSelectAll: () -> Unit
) {
    private val context = editText.context
    private val popup = PopupWindow(context).apply {
        isOutsideTouchable = true
        isFocusable = false
        setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        elevation = Ui.dpf(context, 10f)
    }

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = Ui.dpf(context, 6f)
            setColor(Color.parseColor("#26282B"))
            setStroke(Ui.dp(context, 1f), Color.parseColor("#383A3E"))
        }
        background = bg
        setPadding(Ui.dp(context, 4f), Ui.dp(context, 2f), Ui.dp(context, 4f), Ui.dp(context, 2f))
    }

    init {
        popup.contentView = container
        popup.width = ViewGroup.LayoutParams.WRAP_CONTENT
        popup.height = ViewGroup.LayoutParams.WRAP_CONTENT

        editText.onSelectionChangeListener = { hasSelection, _, _ ->
            if (hasSelection) {
                show()
            } else {
                dismiss()
            }
        }
    }

    fun show() {
        val text = editText.text ?: return
        val editable = isEditable()
        val hasSelection = editText.hasSelection()
        val hasClip = hasClipboard()

        if (text.isEmpty() && (!editable || !hasClip)) {
            dismiss()
            return
        }

        container.removeAllViews()

        val itemParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Ui.dp(context, 38f)
        )

        fun createItem(title: String, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                layoutParams = itemParams
                this.text = title
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(Ui.dp(context, 14f), 0, Ui.dp(context, 14f), 0)
                isClickable = true
                isFocusable = true
                val attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
                val typedArray = context.obtainStyledAttributes(attrs)
                val resId = typedArray.getResourceId(0, 0)
                typedArray.recycle()
                setBackgroundResource(resId)
                setOnClickListener {
                    onClick()
                    dismiss()
                }
            }
        }

        fun createDivider(): View {
            return View(context).apply {
                layoutParams = LinearLayout.LayoutParams(Ui.dp(context, 1f), Ui.dp(context, 18f)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                setBackgroundColor(Color.parseColor("#3E4146"))
            }
        }

        val items = mutableListOf<TextView>()

        if (hasSelection) {
            val selStart = minOf(editText.selectionStart, editText.selectionEnd)
            val selEnd = maxOf(editText.selectionStart, editText.selectionEnd)
            val selectedSnippet = if (selStart in 0..selEnd && selEnd <= text.length) {
                text.subSequence(selStart, selEnd).toString()
            } else ""

            if (selectedSnippet.isNotEmpty()) {
                if (editable && editText.isFocusable && editText.isEnabled) {
                    items.add(createItem("Cut") { onCut(selectedSnippet) })
                }
                items.add(createItem("Copy") { onCopy(selectedSnippet) })
            }
            if (editable && hasClip && editText.isFocusable && editText.isEnabled) {
                items.add(createItem("Paste") { onPaste() })
            }
            if (selStart > 0 || selEnd < text.length) {
                items.add(createItem("Select All") {
                    onSelectAll()
                    editText.post { show() }
                })
            }
        } else {
            if (editable && hasClip && editText.isFocusable && editText.isEnabled) {
                items.add(createItem("Paste") { onPaste() })
            }
            if (text.isNotEmpty()) {
                items.add(createItem("Select All") {
                    onSelectAll()
                    editText.post { show() }
                })
            }
        }

        if (items.isEmpty()) {
            dismiss()
            return
        }

        items.forEachIndexed { index, tv ->
            container.addView(tv)
            if (index < items.size - 1) {
                container.addView(createDivider())
            }
        }

        try {
            if (!editText.isAttachedToWindow) return
            container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val popupWidth = container.measuredWidth
            val popupHeight = container.measuredHeight

            val layout = editText.layout
            val selStart = minOf(editText.selectionStart, editText.selectionEnd).coerceAtLeast(0)
            val line = layout?.getLineForOffset(selStart) ?: 0
            val lineTop = layout?.getLineTop(line) ?: 0
            val lineBottom = layout?.getLineBottom(line) ?: editText.height
            val charX = layout?.getPrimaryHorizontal(selStart)?.toInt() ?: (editText.width / 2)

            val location = IntArray(2)
            editText.getLocationOnScreen(location)

            val screenW = editText.resources.displayMetrics.widthPixels
            val screenH = editText.resources.displayMetrics.heightPixels
            val x = (location[0] + charX - popupWidth / 2).coerceIn(
                Ui.dp(context, 12f),
                (screenW - popupWidth - Ui.dp(context, 12f)).coerceAtLeast(Ui.dp(context, 12f))
            )
            
            // Prefer placing directly above the selected line
            var y = location[1] + lineTop - popupHeight - Ui.dp(context, 12f)
            // If cut off at top of screen or above the window, place directly below the line
            if (y < Ui.dp(context, 35f)) {
                y = location[1] + lineBottom + Ui.dp(context, 8f)
            }
            // Clamp within screen boundaries
            y = y.coerceIn(Ui.dp(context, 30f), screenH - popupHeight - Ui.dp(context, 20f))

            if (popup.isShowing) {
                popup.update(x, y, popupWidth, popupHeight)
            } else {
                popup.showAtLocation(editText.rootView ?: editText, Gravity.NO_GRAVITY, x, y)
            }
        } catch (e: Exception) {
            try {
                if (!popup.isShowing && editText.isAttachedToWindow) {
                    popup.showAsDropDown(editText, 0, -editText.height - Ui.dp(context, 48f))
                }
            } catch (_: Exception) {}
        }
    }

    fun dismiss() {
        if (popup.isShowing) {
            try { popup.dismiss() } catch (_: Exception) {}
        }
    }

    private fun hasClipboard(): Boolean {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val clip = cm.primaryClip
        return clip != null && clip.itemCount > 0 && !clip.getItemAt(0)?.coerceToText(context).isNullOrEmpty()
    }
}

