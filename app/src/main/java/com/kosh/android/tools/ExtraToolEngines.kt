package com.kosh.android.tools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private fun Context.copyUriToCache(uri: Uri, dir: File, prefix: String, suffix: String): File {
    val file = File.createTempFile(prefix, suffix, dir)
    try {
        val input = contentResolver.openInputStream(uri) ?: throw IOException("Can't read the file")
        input.use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }
    } catch (e: Exception) {
        file.delete()
        throw e
    }
    return file
}

/** Excel (.xlsx) to PDF, on the device, using the same sheet renderer the Home tab uses to view spreadsheets. */
class XlsxPdfEngine(private val context: Context, private val runner: PdfToolsRunner) {

    private val converter = OfficePdfConverter(context)
    private val workDir = File(context.cacheDir, "xlsx-tool").apply { mkdirs() }

    /** True when the file is a spreadsheet package (a ZIP that holds xl/workbook.xml). */
    suspend fun looksLikeXlsx(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val copy = runCatching { context.copyUriToCache(uri, workDir, "in", ".zip") }.getOrNull() ?: return@withContext false
        try {
            ZipFile(copy).use { it.getEntry("xl/workbook.xml") != null }
        } catch (e: Exception) {
            false
        } finally {
            copy.delete()
        }
    }

    suspend fun convert(source: Uri, name: String, destination: Uri, onProgress: (Int, Int) -> Unit): Result<List<ToolOutput>> =
        withContext(Dispatchers.IO) {
            val temporary = ArrayList<File>()
            try {
                onProgress(0, 0)
                val copy = context.copyUriToCache(source, workDir, "in", ".xlsx").also { temporary += it }
                val pdf = File(workDir, "out-${System.nanoTime()}.pdf").also { temporary += it }
                converter.convert(copy, FileKind.XLSX, pdf).getOrThrow()
                currentCoroutineContext().ensureActive()
                val out = context.contentResolver.openOutputStream(destination, "w") ?: throw IOException("Can't open the destination")
                BufferedOutputStream(out).use { o -> pdf.inputStream().use { it.copyTo(o) } }
                Result.success(listOf(runner.describe(destination, runner.pageCountOf(destination).coerceAtLeast(0), name)))
            } catch (e: CancellationException) {
                withContext(NonCancellable) { runner.delete(destination) }
                throw e
            } catch (e: Exception) {
                runner.delete(destination)
                Result.failure(if (e is DocxException) IOException(e.message, e) else e)
            } catch (e: OutOfMemoryError) {
                runner.delete(destination)
                Result.failure(IOException("This spreadsheet is too big to convert on this device"))
            } finally {
                temporary.forEach { it.delete() }
            }
        }
}

class NoImagesException : IOException("This PDF has no embedded pictures to extract")

/**
 * Pulls every picture embedded in a PDF (the original image data, not page snapshots) into one ZIP. Pictures that appear
 * on several pages are saved once. JPEG pictures are copied byte for byte, everything else is saved as PNG.
 */
class ImageExtractEngine(private val context: Context, private val runner: PdfToolsRunner) {

    private val workDir = File(context.cacheDir, "extract-images").apply { mkdirs() }

    suspend fun extract(
        source: Uri,
        baseName: String,
        destination: Uri,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Result<List<ToolOutput>> = withContext(Dispatchers.IO) {
        var copy: File? = null
        try {
            copy = context.copyUriToCache(source, workDir, "pdf", ".pdf")
            val count = writeZip(copy, baseName, destination, onProgress)
            val out = runner.describe(destination, count, toolFileName(baseName, "images", "zip"))
            Result.success(listOf(out))
        } catch (e: CancellationException) {
            withContext(NonCancellable) { runner.delete(destination) }
            throw e
        } catch (e: InvalidPasswordException) {
            runner.delete(destination)
            Result.failure(WrongPasswordException())
        } catch (e: Exception) {
            runner.delete(destination)
            Result.failure(e)
        } catch (e: OutOfMemoryError) {
            runner.delete(destination)
            Result.failure(IOException("A picture in this PDF is too big to extract on this device"))
        } finally {
            copy?.delete()
        }
    }

    private suspend fun writeZip(pdf: File, baseName: String, destination: Uri, onProgress: (Int, Int) -> Unit): Int {
        val stream = context.contentResolver.openOutputStream(destination, "w") ?: throw IOException("Can't open the destination")
        var saved = 0
        PDDocument.load(pdf).use { doc ->
            val seen = HashSet<COSBase>()
            val pages = doc.numberOfPages
            ZipOutputStream(BufferedOutputStream(stream)).use { zip ->
                for (p in 0 until pages) {
                    currentCoroutineContext().ensureActive()
                    onProgress(p, pages)
                    val found = ArrayList<PDImageXObject>()
                    collect(doc.getPage(p).resources, seen, found, 0)
                    for ((i, image) in found.withIndex()) {
                        saved += 1
                        writeImage(zip, image, imageEntryName(baseName, p + 1, i + 1, saved), saved)
                    }
                }
                if (saved == 0) throw NoImagesException()
                onProgress(pages, pages)
            }
        }
        return saved
    }

    private fun collect(res: PDResources?, seen: HashSet<COSBase>, out: MutableList<PDImageXObject>, depth: Int) {
        if (res == null || depth > 8) return
        for (name: COSName in res.xObjectNames) {
            val x = runCatching { res.getXObject(name) }.getOrNull() ?: continue
            if (!seen.add(x.cosObject)) continue
            when (x) {
                is PDImageXObject -> if (x.width > 1 && x.height > 1) out += x
                is PDFormXObject -> collect(x.resources, seen, out, depth + 1)
                else -> Unit
            }
        }
    }

    private fun writeImage(zip: ZipOutputStream, image: PDImageXObject, entryName: String, number: Int) {
        val raw = if (image.suffix.equals("jpg", true) || image.suffix.equals("jpeg", true)) rawJpeg(image) else null
        if (raw != null) {
            zip.putNextEntry(ZipEntry("$entryName.jpg"))
            zip.write(raw)
            zip.closeEntry()
            return
        }
        val bitmap = image.image ?: throw IOException("Picture $number couldn't be read")
        try {
            zip.putNextEntry(ZipEntry("$entryName.png"))
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)) throw IOException("Couldn't write picture $number")
            zip.closeEntry()
        } finally {
            bitmap.recycle()
        }
    }

    /** The JPEG exactly as stored in the PDF, or null if it can't be read that way (the picture is then saved as PNG). */
    private fun rawJpeg(image: PDImageXObject): ByteArray? = try {
        image.stream.createInputStream(listOf(COSName.DCT_DECODE.name)).use { it.readBytes() }
            .takeIf { it.size > 3 && it[0] == 0xFF.toByte() && it[1] == 0xD8.toByte() }
    } catch (e: Exception) {
        null
    }
}
