package com.kosh.android.tools

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.kosh.android.reader.PdfRenderEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.IOException

sealed interface ToolDestination {
    /** A document the user just created in the system file picker. */
    data class File(val uri: Uri) : ToolDestination

    /** A folder the user chose; every output is created inside it. */
    data class Folder(val treeUri: Uri) : ToolDestination
}

data class ToolOutput(val name: String, val uri: Uri, val pages: Int, val sizeBytes: Long?)

/**
 * Saving pages of a PDF as pictures, and the small file helpers shared with [PdfBoxEngine]. Pages are drawn with Android's
 * PdfRenderer ([PdfRenderEngine]).
 */
class PdfToolsRunner(context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    /** Saves each listed page as a picture inside [folder]. */
    suspend fun writeImages(
        engine: PdfRenderEngine,
        pages: List<Int>,
        baseName: String,
        format: ImageFormat,
        dpi: Int,
        folder: Uri,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Result<List<ToolOutput>> = withContext(Dispatchers.IO) {
        val created = ArrayList<Uri>()
        try {
            require(pages.isNotEmpty()) { "no pages" }
            val outputs = ArrayList<ToolOutput>()
            pages.forEachIndexed { i, page ->
                currentCoroutineContext().ensureActive()
                onProgress(i, pages.size)
                val size = engine.pageSize(page)
                val bitmap = engine.render(page, renderWidthPx(size.widthPts, dpi)) ?: throw IOException("Page ${page + 1} couldn't be read")
                try {
                    val name = imageFileName(baseName, page + 1, engine.pageCount, format.extension)
                    val target = createInFolder(folder, format.mime, name).also { created += it }
                    val stream = resolver.openOutputStream(target, "w") ?: throw IOException("Can't open the destination")
                    BufferedOutputStream(stream).use { out ->
                        val ok = when (format) {
                            ImageFormat.JPG -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                            ImageFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        if (!ok) throw IOException("Couldn't write $name")
                    }
                    outputs += ToolOutput(nameOf(target) ?: name, target, 1, sizeOf(target))
                } finally {
                    bitmap.recycle()
                }
            }
            onProgress(pages.size, pages.size)
            Result.success(outputs)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { created.forEach { delete(it) } }
            throw e
        } catch (e: Exception) {
            created.forEach { delete(it) }
            Result.failure(e)
        }
    }

    internal fun looksLikePdf(uri: Uri): Boolean = try {
        resolver.openInputStream(uri)?.use { input ->
            val head = ByteArray(5)
            input.read(head) == 5 && String(head, Charsets.ISO_8859_1) == "%PDF-"
        } ?: false
    } catch (e: Exception) {
        false
    }

    /** Name and size of a file that was just written, for the result screen. */
    fun describe(uri: Uri, pages: Int, fallbackName: String): ToolOutput = ToolOutput(nameOf(uri) ?: fallbackName, uri, pages, sizeOf(uri))

    // ---- files -------------------------------------------------------------------------------------------------------

    internal fun createInFolder(tree: Uri, mime: String, name: String): Uri {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return DocumentsContract.createDocument(resolver, parent, mime, name) ?: throw IOException("Can't create $name")
    }

    internal fun pageCountOf(uri: Uri): Int = try {
        resolver.openFileDescriptor(uri, "r")?.use { pfd -> PdfRenderer(pfd).use { it.pageCount } } ?: -1
    } catch (e: Exception) {
        -1
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

    /** Only ever called on documents this run just created, so it can't touch anyone's existing file. */
    internal fun delete(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
    }
}
