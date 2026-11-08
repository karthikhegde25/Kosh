package com.kosh.android.reader.annotation

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.DocumentsContract
import com.kosh.android.pdf.JpegPdfWriter
import com.kosh.android.pdf.PageGeometry
import com.kosh.android.reader.PdfDocumentInfo
import com.kosh.android.reader.PdfRenderEngine
import com.kosh.android.util.DrawItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Writes a NEW PDF that has the annotations burned into the pages. The original PDF is never touched.
 * Pages are rendered to images, so text in the copy can't be selected or searched; the original keeps its text.
 */
class AnnotatedPdfExporter(private val resolver: ContentResolver) {

    suspend fun export(
        engine: PdfRenderEngine,
        info: PdfDocumentInfo,
        itemsByPage: Map<Int, List<DrawItem>>,
        destination: Uri,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val stream = resolver.openOutputStream(destination, "w") ?: throw IOException("Can't open the destination")
            BufferedOutputStream(stream).use { out ->
                val writer = JpegPdfWriter(out, info.pageCount, info.name)
                writer.begin()
                for (index in 0 until info.pageCount) {
                    currentCoroutineContext().ensureActive()
                    onProgress(index, info.pageCount)
                    val bitmap = engine.render(index, RENDER_WIDTH_PX) ?: throw IOException("Page ${index + 1} couldn't be rendered")
                    try {
                        Canvas(bitmap).let { AnnotationPainter.draw(it, itemsByPage[index].orEmpty(), bitmap.width.toFloat(), bitmap.height.toFloat()) }
                        val jpeg = ByteArrayOutputStream(bitmap.byteCount / 8).also {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
                        }.toByteArray()
                        val size = info.pageSizes[index]
                        val w = size.widthPts.toFloat()
                        val h = size.heightPts.toFloat()
                        writer.addPage(JpegPdfWriter.PageImage(jpeg, bitmap.width, bitmap.height, PageGeometry(w, h, 0f, 0f, w, h)))
                    } finally {
                        bitmap.recycle()
                    }
                }
                writer.finish()
            }
            onProgress(info.pageCount, info.pageCount)
            Result.success(Unit)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { deletePartial(destination) }
            throw e
        } catch (e: Exception) {
            deletePartial(destination)
            Result.failure(e)
        }
    }

    private fun deletePartial(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
    }

    private companion object {
        const val RENDER_WIDTH_PX = 1654 // about 200 dpi on an A4 page
        const val JPEG_QUALITY = 88
    }
}
