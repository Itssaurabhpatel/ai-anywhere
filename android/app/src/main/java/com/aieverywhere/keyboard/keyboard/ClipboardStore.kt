package com.aieverywhere.keyboard.keyboard

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

data class ClipItem(
    val id: Long,
    val type: String,          // "text" | "image"
    val text: String?,
    val path: String?,
    val time: Long,
    val origin: String?,       // de-duplication key (e.g. MediaStore id)
    val isScreenshot: Boolean = false,
    var isPasted: Boolean = false,
    var isPinned: Boolean = false
)

/** Clipboard history that survives restarts and also keeps copied images and screenshots. */
object ClipboardStore {
    const val PREF_ENABLED = "CLIP_ENABLED"
    const val PREF_SCREENSHOTS = "CLIP_SCREENSHOTS"
    private const val MAX_ITEMS = 50
    private const val FILE = "clipboard_history.json"
    private const val EXPIRY_TIME_MS = 3600_000L // 1 hour (same as Gboard)

    private var items: MutableList<ClipItem>? = null
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun isEnabled(ctx: Context) = ThemeManager.prefs(ctx).getBoolean(PREF_ENABLED, true)
    fun setEnabled(ctx: Context, on: Boolean) = ThemeManager.prefs(ctx).edit().putBoolean(PREF_ENABLED, on).apply()

    @Synchronized
    fun all(ctx: Context): List<ClipItem> {
        cleanupExpired(ctx)
        return load(ctx).toList()
    }

    @Synchronized
    private fun cleanupExpired(ctx: Context) {
        val now = System.currentTimeMillis()
        val list = load(ctx)
        val expired = list.filter { !it.isPinned && (now - it.time) > EXPIRY_TIME_MS }
        if (expired.isNotEmpty()) {
            expired.forEach { it.path?.let { p -> try { File(p).delete() } catch (_: Exception) {} } }
            list.removeAll(expired)
            persist(ctx)
        }
    }

    @Synchronized
    private fun load(ctx: Context): MutableList<ClipItem> {
        items?.let { return it }
        val list = mutableListOf<ClipItem>()
        try {
            val f = File(ctx.filesDir, FILE)
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val item = ClipItem(
                        o.getLong("id"), o.getString("type"),
                        if (o.isNull("text")) null else o.optString("text"),
                        if (o.isNull("path")) null else o.optString("path"),
                        o.optLong("time"), if (o.isNull("origin")) null else o.optString("origin"),
                        o.optBoolean("shot", false), o.optBoolean("pasted", false),
                        o.optBoolean("pinned", false)
                    )
                    if (item.type == "text" || (item.path != null && File(item.path).exists() && item.isScreenshot)) list += item
                    else if (item.path != null) {
                        try { File(item.path).delete() } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            // corrupt file: start fresh
        }
        items = list
        return list
    }

    @Synchronized
    private fun persist(ctx: Context) {
        val arr = JSONArray()
        for (it in load(ctx)) {
            arr.put(JSONObject().apply {
                put("id", it.id); put("type", it.type); put("text", it.text ?: JSONObject.NULL)
                put("path", it.path ?: JSONObject.NULL); put("time", it.time)
                put("origin", it.origin ?: JSONObject.NULL); put("shot", it.isScreenshot)
                put("pasted", it.isPasted); put("pinned", it.isPinned)
            })
        }
        try {
            File(ctx.filesDir, FILE).writeText(arr.toString())
        } catch (e: Exception) {
        }
    }

    @Synchronized
    private fun insert(ctx: Context, item: ClipItem) {
        val list = load(ctx)
        list.add(0, item)
        // Keep pinned items, prune unpinned items when over MAX_ITEMS
        while (list.size > MAX_ITEMS) {
            val lastUnpinnedIndex = list.indexOfLast { !it.isPinned }
            if (lastUnpinnedIndex >= 0) {
                val removed = list.removeAt(lastUnpinnedIndex)
                removed.path?.let { File(it).delete() }
            } else {
                break
            }
        }
        persist(ctx)
    }

    @Synchronized
    fun togglePin(ctx: Context, id: Long): Boolean {
        val item = load(ctx).find { it.id == id } ?: return false
        item.isPinned = !item.isPinned
        persist(ctx)
        return item.isPinned
    }

    @Synchronized
    fun markPasted(ctx: Context, id: Long) {
        val item = load(ctx).find { it.id == id }
        if (item != null) {
            item.isPasted = true
            persist(ctx)
        }
    }

    @Synchronized
    fun addText(ctx: Context, text: String) {
        if (text.isBlank()) return
        val list = load(ctx)
        val existing = list.find { it.type == "text" && it.text == text }
        val wasPinned = existing?.isPinned ?: false
        list.removeAll { it.type == "text" && it.text == text }
        insert(ctx, ClipItem(System.currentTimeMillis(), "text", text, null, System.currentTimeMillis(), null, isPinned = wasPinned))
    }

    @Synchronized
    fun hasOrigin(ctx: Context, origin: String) = load(ctx).any { it.origin == origin }

    /** Copies an image (clipboard image or screenshot) into private storage, off the main thread. */
    fun addImageAsync(ctx: Context, uri: Uri, origin: String, screenshot: Boolean, done: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        io.execute {
            if (hasOrigin(app, origin)) return@execute
            try {
                val dir = File(app.filesDir, "clips").apply { mkdirs() }
                val type = app.contentResolver.getType(uri) ?: "image/png"
                val ext = when {
                    type.contains("jpeg") || type.contains("jpg") -> "jpg"
                    type.contains("webp") -> "webp"
                    type.contains("gif") -> "gif"
                    else -> "png"
                }
                val id = System.currentTimeMillis()
                val f = File(dir, "clip_$id.$ext")
                val copied = app.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(f).use { input.copyTo(it) }
                }
                if (copied == null || f.length() == 0L) {
                    f.delete(); return@execute
                }
                insert(app, ClipItem(id, "image", null, f.absolutePath, id, origin, screenshot))
                done?.let { main.post(it) }
            } catch (e: Exception) {
                // no permission / file gone - ignore
            }
        }
    }

    @Synchronized
    fun remove(ctx: Context, item: ClipItem) {
        load(ctx).removeAll { it.id == item.id }
        item.path?.let { File(it).delete() }
        persist(ctx)
    }

    @Synchronized
    fun clear(ctx: Context) {
        load(ctx).forEach { it.path?.let { p -> File(p).delete() } }
        load(ctx).clear()
        persist(ctx)
    }
}

/** Watches MediaStore for new screenshots and adds them to the clipboard history. */
class ScreenshotWatcher(private val ctx: Context, private val onAdded: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var observer: ContentObserver? = null

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(): Boolean = ctx.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED ||
        (Build.VERSION.SDK_INT >= 34 &&
            ctx.checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED)

    fun isWanted(): Boolean = ThemeManager.prefs(ctx).getBoolean(ClipboardStore.PREF_SCREENSHOTS, true)

    fun start() {
        if (observer != null || !hasPermission() || !isWanted()) return
        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scan(maxAgeSec = 300, limit = 3)
            }
        }
        try {
            ctx.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, obs)
            observer = obs
        } catch (e: Exception) {
        }
    }

    fun stop() {
        observer?.let { try { ctx.contentResolver.unregisterContentObserver(it) } catch (e: Exception) {} }
        observer = null
    }

    /** Adds screenshots taken in the last [maxAgeSec] seconds that aren't in the history yet. */
    fun scan(maxAgeSec: Long = 7 * 86400, limit: Int = 10) {
        if (!hasPermission() || !isWanted() || !ClipboardStore.isEnabled(ctx)) return
        val since = (System.currentTimeMillis() / 1000) - maxAgeSec
        val cols = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATA
        )
        if (Build.VERSION.SDK_INT >= 29) {
            cols += MediaStore.Images.Media.RELATIVE_PATH
        }
        var selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        if (Build.VERSION.SDK_INT >= 29) selection += " AND ${MediaStore.Images.Media.IS_PENDING} = 0"
        try {
            ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols.toTypedArray(), selection,
                arrayOf(since.toString()), "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { c ->
                var found = 0
                val dataIdx = c.getColumnIndex(MediaStore.Images.Media.DATA)
                val relIdx = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH) else -1
                while (c.moveToNext() && found < limit) {
                    val id = c.getLong(0)
                    val displayName = (c.getString(1) ?: "").lowercase()
                    val fullPath = (if (dataIdx >= 0) c.getString(dataIdx) else null)?.lowercase().orEmpty()
                    val relPath = (if (relIdx >= 0) c.getString(relIdx) else null)?.lowercase().orEmpty()
                    val combinedPath = "$fullPath $relPath"

                    // Exclude regular camera photos, downloads, and chat media
                    if (combinedPath.contains("/camera") || combinedPath.contains("camera/") ||
                        combinedPath.contains("/whatsapp") || combinedPath.contains("/download") ||
                        combinedPath.contains("dcim/100") || displayName.startsWith("img_") ||
                        displayName.startsWith("vid_")) {
                        continue
                    }

                    // Must be in a Screenshots directory or named like a screenshot
                    val isShot = combinedPath.contains("screenshot") ||
                                 displayName.contains("screenshot") ||
                                 displayName.contains("screen_") ||
                                 displayName.contains("screen-") ||
                                 displayName.contains("screencap")
                    if (!isShot) continue

                    found++
                    val origin = "ms:$id"
                    if (ClipboardStore.hasOrigin(ctx, origin)) continue
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    ClipboardStore.addImageAsync(ctx, uri, origin, true, onAdded)
                }
            }
        } catch (e: Exception) {
        }
    }
}
