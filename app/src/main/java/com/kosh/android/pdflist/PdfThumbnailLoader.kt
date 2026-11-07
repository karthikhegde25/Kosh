package com.kosh.android.pdflist

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Renders the first page of a PDF as a small preview for the Home list. Results are cached (keyed by path and
 * modification time, so an edited file gets a fresh preview) and at most two PDFs are opened at once, so a long
 * list scrolls smoothly. Password-protected or damaged PDFs return null and the list shows a PDF icon instead.
 */
object PdfThumbnailLoader {

    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val gate = Semaphore(2)

    suspend fun load(path: String, modifiedMillis: Long, widthPx: Int): Bitmap? {
        val key = "$path|$modifiedMillis|$widthPx"
        cache.get(key)?.let { return it }
        val bitmap = gate.withPermit {
            withContext(Dispatchers.IO) { renderFirstPage(path, widthPx) }
        }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    private fun renderFirstPage(path: String, widthPx: Int): Bitmap? = try {
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount <= 0) return@use null
                val page = renderer.openPage(0)
                try {
                    val w = max(1, widthPx)
                    val h = max(1, (w * page.height.toFloat() / max(1, page.width)).roundToInt())
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE) // unpainted areas would otherwise be transparent
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    page.close()
                }
            }
        }
    } catch (e: Exception) { // password-protected (SecurityException), damaged, or unreadable
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}
