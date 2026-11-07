package com.kosh.android.pdf

import android.content.ContentResolver
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.kosh.android.data.model.ScannedPage
import com.kosh.android.imaging.PageRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

data class ExportResult(val uri: Uri, val name: String, val pageCount: Int, val sizeBytes: Long?)

/** Renders the scanned pages one at a time and streams them into a real multi-page PDF at [destination]. */
class PdfGenerator(private val resolver: ContentResolver) {

    suspend fun generate(
        pages: List<ScannedPage>,
        title: String,
        displayName: String,
        options: PdfExportOptions,
        destination: Uri,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        try {
            require(pages.isNotEmpty()) { "no pages" }
            val stream = resolver.openOutputStream(destination, "w") ?: throw IOException("Can't open the destination")
            BufferedOutputStream(stream).use { out ->
                val writer = JpegPdfWriter(out, pages.size, title)
                writer.begin()
                pages.forEachIndexed { index, page ->
                    currentCoroutineContext().ensureActive()
                    onProgress(index, pages.size)
                    val bitmap = PageRenderer.renderFinal(page, options.quality.maxDimPx)
                        ?: throw IOException("Page ${index + 1} couldn't be read")
                    try {
                        val jpeg = ByteArrayOutputStream(bitmap.byteCount / 8).also {
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, options.quality.jpegQuality, it)
                        }.toByteArray()
                        writer.addPage(
                            JpegPdfWriter.PageImage(jpeg, bitmap.width, bitmap.height, computeGeometry(bitmap.width, bitmap.height, options)),
                        )
                    } finally {
                        bitmap.recycle()
                    }
                }
                writer.finish()
            }
            onProgress(pages.size, pages.size)
            // Reopen it: a PDF that can't be read back is reported as a failure instead of a success.
            check(verifyPageCount(destination, pages.size)) { "The saved file isn't a valid PDF" }
            Result.success(ExportResult(destination, nameOf(destination) ?: displayName, pages.size, sizeOf(destination)))
        } catch (e: CancellationException) {
            withContext(NonCancellable) { deletePartial(destination) }
            throw e
        } catch (e: Exception) {
            deletePartial(destination)
            Result.failure(e)
        }
    }

    private fun verifyPageCount(uri: Uri, expected: Int): Boolean = try {
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { it.pageCount == expected }
        } ?: false
    } catch (e: Exception) {
        false
    }

    private fun nameOf(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    private fun sizeOf(uri: Uri): Long? = try {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    } catch (e: Exception) {
        null
    }

    /** The picker just created this document, so removing it on failure can't touch anyone's existing file. */
    private fun deletePartial(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
    }
}
