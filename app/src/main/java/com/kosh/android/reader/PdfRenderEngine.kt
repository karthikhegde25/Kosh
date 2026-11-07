package com.kosh.android.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.kosh.android.util.computeRenderSize

/** Renderer-specific code lives behind this interface so the engine can be swapped later. */
interface PdfRenderEngine {
    val pageCount: Int
    fun pageSize(index: Int): PageSize

    /** Renders [index] about [targetWidthPx] wide. Returns null if closed or the page can't be rendered. */
    fun render(index: Int, targetWidthPx: Int): Bitmap?
    fun close()
}

/**
 * Baseline engine on Android's [PdfRenderer]. The renderer allows only one open page at a time and
 * is not thread-safe, so every call is serialised on [lock].
 */
class AndroidPdfRenderEngine(
    private val renderer: PdfRenderer,
    private val descriptor: ParcelFileDescriptor,
) : PdfRenderEngine {

    private val lock = Any()
    private var closed = false

    override val pageCount: Int = renderer.pageCount

    override fun pageSize(index: Int): PageSize = synchronized(lock) {
        val page = renderer.openPage(index)
        try {
            PageSize(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1))
        } finally {
            page.close()
        }
    }

    override fun render(index: Int, targetWidthPx: Int): Bitmap? = synchronized(lock) {
        if (closed) return null
        try {
            val page = renderer.openPage(index)
            try {
                val (w, h) = computeRenderSize(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1), targetWidthPx)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE) // PdfRenderer leaves unpainted areas transparent
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
        }
    }
}
