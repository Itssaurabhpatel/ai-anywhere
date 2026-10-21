package com.aieverywhere.keyboard.ui

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.aieverywhere.keyboard.R
import com.aieverywhere.keyboard.keyboard.StickerStore
import com.aieverywhere.keyboard.keyboard.ThemeManager
import com.aieverywhere.keyboard.keyboard.Ui
import java.io.File

/** Create stickers from your own photos; they show up in the keyboard's Stickers tab. */
@SuppressLint("SetTextI18n")
class StickerManagerActivity : AppCompatActivity() {

    private lateinit var grid: GridLayout
    private lateinit var status: TextView
    private val prefs by lazy { ThemeManager.prefs(this) }
    private var deletingFile: File? = null

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishAndRemoveTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Status bar color to match background
        window.statusBarColor = Color.parseColor("#F5F7FA")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(48))
            setBackgroundColor(Color.parseColor("#F5F7FA"))
        }
        
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
            
            addView(TextView(this@StickerManagerActivity).apply {
                text = "❮ "
                textSize = 28f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#7B1FA2"))
                setOnClickListener { finishAndRemoveTask() }
                setPadding(0, 0, dp(8), 0)
            })
            
            addView(TextView(this@StickerManagerActivity).apply {
                text = "My Stickers"
                textSize = 28f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#1C1C1E"))
            })
        }
        content.addView(header)
        content.addView(TextView(this).apply {
            text = "Pick photos from your gallery. They become stickers in the keyboard (tap the sticker icon in the toolbar, then the sticker tab)."
            textSize = 15f
            setTextColor(Color.parseColor("#5F6368"))
            setPadding(0, 0, 0, dp(24))
            setLineSpacing(dp(4).toFloat(), 1f)
        })
        content.addView(TextView(this).apply {
            text = "Sticker shape"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1C1E"))
            setPadding(0, 0, 0, dp(12))
        })
        content.addView(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, 0, 0, dp(24))
            val current = prefs.getString(StickerStore.PREF_SHAPE, "rounded")
            listOf("rounded" to "Rounded", "circle" to "Circle", "square" to "Square").forEach { (id, name) ->
                addView(RadioButton(this@StickerManagerActivity).apply {
                    layoutParams = RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    text = name
                    this.id = View.generateViewId()
                    isChecked = id == current
                    textSize = 15f
                    setPadding(dp(4), 0, 0, 0)
                    setOnCheckedChangeListener { _, on -> if (on) prefs.edit().putString(StickerStore.PREF_SHAPE, id).apply() }
                })
            }
        })
        content.addView(Button(this).apply {
            text = "+ Add photos"
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            background = Ui.rounded(Color.parseColor("#7B1FA2"), Ui.dpf(this@StickerManagerActivity, 24f))
            setOnClickListener { pick() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, dp(24), 0, dp(12))
        }
        content.addView(status)
        
        grid = GridLayout(this).apply { columnCount = 3 }
        content.addView(grid)
        
        setContentView(ScrollView(this).apply { 
            addView(content)
            setBackgroundColor(Color.parseColor("#F5F7FA"))
        })
        refresh()
    }

    private fun dp(v: Int) = Ui.dp(this, v.toFloat())

    private fun refresh() {
        grid.removeAllViews()
        val mine = StickerStore.user(this)
        status.text = if (mine.isEmpty()) "No photo stickers yet. The built-in pack is always available in the keyboard."
        else "Your stickers (" + mine.size + ") — long-press one to delete"
        
        val size = (resources.displayMetrics.widthPixels - dp(48)) / 3 - dp(8)
        
        for (f in mine) {
            val frame = android.widget.FrameLayout(this).apply {
                val pad = dp(4)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(pad, pad, pad, pad)
                }
                
                val iv = ImageView(this@StickerManagerActivity).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageBitmap(StickerStore.thumbnail(f, size))
                }
                
                val delBtn = ImageView(this@StickerManagerActivity).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(dp(28), dp(28)).apply {
                        gravity = Gravity.TOP or Gravity.END
                        setMargins(0, dp(4), dp(4), 0)
                    }
                    setImageResource(R.drawable.ic_clear)
                    background = Ui.rounded(0xDD000000.toInt(), Ui.dpf(context, 14f))
                    Ui.tint(this, 0xFFFFFFFF.toInt())
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                    elevation = Ui.dpf(this@StickerManagerActivity, 6f)
                    visibility = if (deletingFile == f) View.VISIBLE else View.GONE
                    
                    setOnClickListener {
                        StickerStore.delete(f)
                        deletingFile = null
                        refresh()
                    }
                }
                
                addView(iv)
                addView(delBtn)
                
                setOnLongClickListener {
                    deletingFile = f
                    refresh()
                    true
                }
                
                setOnClickListener {
                    if (deletingFile != null) {
                        deletingFile = null
                        refresh()
                    }
                }
            }
            grid.addView(frame)
        }
    }

    private fun pick() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, REQ_PICK)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        data.clipData?.let { c -> for (i in 0 until c.itemCount) uris += c.getItemAt(i).uri }
        if (uris.isEmpty()) data.data?.let { uris += it }
        status.text = "Making stickers..."
        Thread {
            val made = uris.count { StickerStore.importImage(this, it) != null }
            runOnUiThread {
                Toast.makeText(this, made.toString() + " sticker(s) added", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }.start()
    }

    companion object {
        private const val REQ_PICK = 51
    }
}
