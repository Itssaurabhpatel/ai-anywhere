package com.aieverywhere.keyboard.keyboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

enum class SendResult { INSERTED, COPIED, FAILED }

/** Built-in sticker pack + stickers the user makes from their own photos. */
object StickerStore {
    const val PREF_SHAPE = "STICKER_SHAPE"   // square | rounded | circle
    private const val SIZE = 512
    private val thumbs = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun userDir(ctx: Context) = File(ctx.filesDir, "stickers").apply { mkdirs() }
    private fun builtInDir(ctx: Context) = File(ctx.filesDir, "stickers_builtin").apply { mkdirs() }

    /** Extracts the bundled pack from assets/stickers the first time it is needed. */
    fun builtIn(ctx: Context): List<File> {
        val dir = builtInDir(ctx)
        val names = try { ctx.assets.list("stickers")?.sorted() ?: emptyList() } catch (e: Exception) { emptyList() }
        return names.mapNotNull { name ->
            val f = File(dir, name)
            if (!f.exists()) {
                try {
                    ctx.assets.open("stickers/$name").use { input -> FileOutputStream(f).use { input.copyTo(it) } }
                } catch (e: Exception) {
                    return@mapNotNull null
                }
            }
            f
        }
    }

    fun user(ctx: Context): List<File> =
        userDir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".png") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun delete(file: File) {
        thumbs.remove(file.absolutePath)
        file.delete()
    }

    /** Turns any photo into a 512x512 sticker PNG (centre crop + chosen shape). */
    fun importImage(ctx: Context, uri: Uri): File? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
            val src = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null

            val side = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
            val shape = ctx.getSharedPreferences(ThemeManager.PREFS, Context.MODE_PRIVATE).getString(PREF_SHAPE, "rounded")
            val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            val r = RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat())
            when (shape) {
                "circle" -> c.drawOval(r, paint)
                "square" -> c.drawRect(r, paint)
                else -> c.drawRoundRect(r, SIZE * 0.14f, SIZE * 0.14f, paint)
            }
            val f = File(userDir(ctx), "sticker_${System.currentTimeMillis()}.png")
            FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (square !== src) square.recycle()
            src.recycle(); scaled.recycle(); out.recycle()
            f
        } catch (e: Throwable) {
            null
        }
    }

    fun thumbnail(file: File, px: Int): Bitmap? {
        val key = file.absolutePath
        thumbs.get(key)?.let { return it }
        val bmp = Ui.decodeScaled(key, px) ?: return null
        thumbs.put(key, bmp)
        return bmp
    }

    fun mimeOf(file: File): String = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> "image/png"
    }

    /**
     * Sends an image into the focused app. Uses the rich-content API when the app supports it
     * (WhatsApp, Telegram, Messages...), otherwise puts the image on the clipboard to paste.
     */
    fun sendImage(svc: InputMethodService, file: File): SendResult {
        val mime = mimeOf(file)
        val shared = try {
            val dir = File(svc.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach { it.delete() }
            File(dir, "img_${System.currentTimeMillis()}.${file.extension.ifEmpty { "png" }}").also { file.copyTo(it, true) }
        } catch (e: Exception) {
            return SendResult.FAILED
        }
        val uri = try {
            FileProvider.getUriForFile(svc, svc.packageName + ".fileprovider", shared)
        } catch (e: Exception) {
            return SendResult.FAILED
        }

        val ei = svc.currentInputEditorInfo
        val ic = svc.currentInputConnection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && ei != null && ic != null) {
            val accepted = ei.contentMimeTypes ?: emptyArray()
            if (accepted.any { ClipDescription.compareMimeTypes(mime, it) }) {
                val info = InputContentInfo(uri, ClipDescription("sticker", arrayOf(mime)), null)
                val ok = try {
                    ic.commitContent(info, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
                } catch (e: Exception) {
                    false
                }
                if (ok) return SendResult.INSERTED
            }
        }
        return try {
            val cm = svc.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newUri(svc.contentResolver, "image", uri))
            SendResult.COPIED
        } catch (e: Exception) {
            SendResult.FAILED
        }
    }

    fun isOwnUri(ctx: Context, uri: Uri?): Boolean = uri?.authority == ctx.packageName + ".fileprovider"
}
