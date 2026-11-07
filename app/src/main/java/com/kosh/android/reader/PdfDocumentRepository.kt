package com.kosh.android.reader

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.coroutineContext

sealed interface OpenOutcome {
    data class Opened(val engine: PdfRenderEngine, val info: PdfDocumentInfo) : OpenOutcome
    data class Failed(val error: ReaderError) : OpenOutcome
}

/** Opens `content://` and `file://` URIs through [android.content.ContentResolver]; never assumes a file path. */
class PdfDocumentRepository(private val context: Context) {

    suspend fun open(uri: Uri): OpenOutcome = withContext(Dispatchers.IO) {
        val descriptor = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: FileNotFoundException) {
            return@withContext OpenOutcome.Failed(ReaderError.NOT_FOUND)
        } catch (e: SecurityException) {
            return@withContext OpenOutcome.Failed(ReaderError.ACCESS_DENIED)
        } catch (e: Exception) {
            return@withContext OpenOutcome.Failed(ReaderError.UNSUPPORTED)
        } ?: return@withContext OpenOutcome.Failed(ReaderError.NOT_FOUND)

        val renderer = try {
            PdfRenderer(descriptor)
        } catch (e: SecurityException) {
            descriptor.closeQuietly()
            return@withContext OpenOutcome.Failed(ReaderError.PASSWORD_PROTECTED)
        } catch (e: IOException) {
            descriptor.closeQuietly()
            return@withContext OpenOutcome.Failed(ReaderError.CORRUPT)
        } catch (e: Exception) {
            descriptor.closeQuietly()
            return@withContext OpenOutcome.Failed(ReaderError.UNSUPPORTED)
        }

        val engine = AndroidPdfRenderEngine(renderer, descriptor)
        if (engine.pageCount <= 0) {
            engine.close()
            return@withContext OpenOutcome.Failed(ReaderError.EMPTY)
        }
        try {
            // Mixed page sizes are supported, so every page's size is read up front.
            val sizes = ArrayList<PageSize>(engine.pageCount)
            for (i in 0 until engine.pageCount) {
                coroutineContext.ensureActive()
                sizes += engine.pageSize(i)
            }
            val (name, size) = queryNameAndSize(uri)
            OpenOutcome.Opened(engine, PdfDocumentInfo(name, engine.pageCount, sizes, size))
        } catch (e: kotlinx.coroutines.CancellationException) {
            engine.close()
            throw e
        } catch (e: Exception) {
            engine.close()
            OpenOutcome.Failed(ReaderError.CORRUPT)
        }
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long?> {
        if (uri.scheme == "file") {
            val f = File(uri.path ?: "")
            return f.name.ifEmpty { "PDF" } to f.length().takeIf { it > 0 }
        }
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val s = c.getColumnIndex(OpenableColumns.SIZE)
                        if (n >= 0) name = c.getString(n)
                        if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                    }
                }
        }
        return (name ?: uri.lastPathSegment ?: "PDF") to size
    }

    private fun ParcelFileDescriptor.closeQuietly() = runCatching { close() }
}
