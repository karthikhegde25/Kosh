package com.kosh.android.tools

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/** What to do to a PDF. Pages are 0-based. */
sealed interface PdfOp {
    /** Joins whole PDFs, in the given order. */
    data class Merge(val sources: List<Uri>) : PdfOp

    /** Keeps only [keep] (or every page when null) after turning the pages in [turn] (page -> extra degrees). */
    data class Pages(val source: Uri, val keep: List<Int>?, val turn: Map<Int, Int> = emptyMap()) : PdfOp

    /** Puts the pages in a new order; [order] lists the original page numbers. */
    data class Reorder(val source: Uri, val order: List<Int>) : PdfOp

    data class Numbers(val source: Uri, val style: NumberingStyle) : PdfOp

    data class Encrypt(
        val source: Uri,
        val password: String,
        val strength: EncryptStrength,
        val allowPrint: Boolean,
        val allowCopy: Boolean,
        val allowEdit: Boolean,
    ) : PdfOp

    data class Decrypt(val source: Uri, val password: String) : PdfOp

    /** Puts the pages of [sources] (read one after the other) onto A4 sheets, several to a sheet. */
    data class NUp(val sources: List<Uri>, val style: NUpStyle) : PdfOp
}

/** One output file: its name (used when the user chose a folder) and what to do to produce it. */
class PdfJob(val name: String, val op: PdfOp, val pages: Int)

class WrongPasswordException : IOException("Wrong password")
class NotProtectedException : IOException("This PDF isn't password protected")

sealed interface Inspection {
    data class Readable(val pages: Int, val encrypted: Boolean) : Inspection
    data object NeedsPassword : Inspection
    data object Unreadable : Inspection
}

/**
 * Edits PDFs with PdfBox-Android. Unlike re-drawing pages as pictures, this keeps text, fonts, vectors, links and bookmarks
 * exactly as they were: pages are moved, removed or turned inside the document, and page numbers are added as real text.
 */
class PdfBoxEngine(context: Context, private val runner: PdfToolsRunner) {

    private val resolver = context.contentResolver
    private val workDir = File(context.cacheDir, "pdfbox").apply { mkdirs() }

    // Large files spill to disk instead of filling memory.
    private fun memory(): MemoryUsageSetting = MemoryUsageSetting.setupMixed(24L * 1024 * 1024).setTempDir(workDir)

    suspend fun run(
        jobs: List<PdfJob>,
        destination: ToolDestination,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Result<List<ToolOutput>> = withContext(Dispatchers.IO) {
        val created = ArrayList<Uri>()
        val copies = HashMap<Uri, File>()
        val temporary = ArrayList<File>()
        try {
            require(jobs.isNotEmpty()) { "nothing to do" }
            val outputs = ArrayList<ToolOutput>()
            val totalPages = jobs.sumOf { it.pages }
            var pagesDone = 0
            jobs.forEach { job ->
                currentCoroutineContext().ensureActive()
                val target = when (destination) {
                    is ToolDestination.File -> destination.uri.also { created += it }
                    is ToolDestination.Folder -> runner.createInFolder(destination.treeUri, "application/pdf", job.name).also { created += it }
                }
                val stream = resolver.openOutputStream(target, "w") ?: throw IOException("Can't open the destination")
                val base = pagesDone
                BufferedOutputStream(stream).use { out ->
                    execute(job.op, out, copies, temporary) { done, total -> onProgress(base + if (total > 0) done else 0, totalPages) }
                }
                val pages = verify(target, job)
                pagesDone += job.pages
                onProgress(pagesDone, totalPages)
                outputs += runner.describe(target, pages, job.name)
            }
            Result.success(outputs)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { created.forEach { runner.delete(it) } }
            throw e
        } catch (e: Exception) {
            created.forEach { runner.delete(it) }
            Result.failure(e)
        } finally {
            copies.values.forEach { it.delete() }
            temporary.forEach { it.delete() }
        }
    }

    /** Opens a PDF only to learn its page count and whether it is password protected. */
    suspend fun inspect(source: Uri, password: String): Inspection = withContext(Dispatchers.IO) {
        val copy = copyToCache(source)
        try {
            PDDocument.load(copy, password, memory()).use { Inspection.Readable(it.numberOfPages, it.isEncrypted) }
        } catch (e: InvalidPasswordException) {
            Inspection.NeedsPassword
        } catch (e: Exception) {
            Inspection.Unreadable
        } finally {
            copy.delete()
        }
    }

    // ---- the operations -------------------------------------------------------------------------------------------

    private suspend fun execute(
        op: PdfOp,
        out: OutputStream,
        copies: MutableMap<Uri, File>,
        temporary: MutableList<File>,
        progress: (Int, Int) -> Unit,
    ) {
        fun fileFor(uri: Uri): File = copies.getOrPut(uri) { copyToCache(uri) }
        fun open(uri: Uri, password: String = ""): PDDocument = try {
            PDDocument.load(fileFor(uri), password, memory())
        } catch (e: InvalidPasswordException) {
            throw WrongPasswordException()
        }

        when (op) {
            is PdfOp.Merge -> {
                progress(0, 0)
                val merger = PDFMergerUtility()
                op.sources.forEach { merger.addSource(fileFor(it)) }
                merger.destinationStream = out
                try {
                    merger.mergeDocuments(memory())
                } catch (e: InvalidPasswordException) {
                    throw IOException("One of the PDFs is password protected. Decrypt it first.")
                }
            }

            is PdfOp.Pages -> open(op.source).use { doc ->
                val count = doc.numberOfPages
                // Turn first, while page numbers still match the original file.
                op.turn.forEach { (page, degrees) ->
                    if (page in 0 until count) {
                        val p = doc.getPage(page)
                        p.rotation = quarterTurns(p.rotation + degrees)
                    }
                }
                op.keep?.let { keep ->
                    val wanted = keep.toSet()
                    for (i in count - 1 downTo 0) if (i !in wanted) doc.removePage(i)
                }
                progress(0, 0)
                doc.save(out)
            }

            is PdfOp.Reorder -> open(op.source).use { doc ->
                val tree = doc.pages
                val count = doc.numberOfPages
                require(op.order.size == count && op.order.toSet() == (0 until count).toSet()) { "Bad page order" }
                val pages = List(count) { tree.get(it) }
                for (i in count - 1 downTo 0) tree.remove(i)
                op.order.forEach { tree.add(pages[it]) }
                progress(0, 0)
                doc.save(out)
            }

            is PdfOp.Numbers -> open(op.source).use { doc ->
                val font = PDType1Font.HELVETICA
                val count = doc.numberOfPages
                val size = op.style.size.points
                for (i in 0 until count) {
                    currentCoroutineContext().ensureActive()
                    progress(i, count)
                    val label = pageLabel(i, count, op.style) ?: continue
                    val page = doc.getPage(i)
                    val box = page.cropBox
                    val textWidth = font.getStringWidth(label) / 1000f * size
                    val at = numberPlacement(op.style.position, page.rotation, box.width, box.height, 28f, textWidth, size)
                    // Appended after the existing content (and wrapped so earlier drawing settings can't affect it).
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.beginText()
                        cs.setFont(font, size)
                        cs.setNonStrokingColor(0f, 0f, 0f)
                        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(at.angleDegrees.toDouble()), box.lowerLeftX + at.x, box.lowerLeftY + at.y))
                        cs.showText(label)
                        cs.endText()
                    }
                }
                progress(count, count)
                doc.save(out)
            }

            is PdfOp.Encrypt -> open(op.source).use { doc ->
                val permissions = AccessPermission()
                permissions.setCanPrint(op.allowPrint)
                permissions.setCanExtractContent(op.allowCopy)
                permissions.setCanExtractForAccessibility(true)
                permissions.setCanModify(op.allowEdit)
                permissions.setCanModifyAnnotations(op.allowEdit)
                permissions.setCanFillInForm(op.allowEdit)
                permissions.setCanAssembleDocument(op.allowEdit)
                // With every permission allowed the owner password can simply match; otherwise it is random, so the
                // restrictions can't be lifted by anyone who only knows the password needed to open the file.
                val owner = if (op.allowPrint && op.allowCopy && op.allowEdit) op.password else UUID.randomUUID().toString()
                val policy = StandardProtectionPolicy(owner, op.password, permissions)
                policy.encryptionKeyLength = op.strength.bits
                policy.isPreferAES = true
                doc.protect(policy)
                progress(0, 0)
                doc.save(out)
            }

            is PdfOp.Decrypt -> open(op.source, op.password).use { doc ->
                if (!doc.isEncrypted) throw NotProtectedException()
                doc.isAllSecurityToBeRemoved = true
                progress(0, 0)
                doc.save(out)
            }

            is PdfOp.NUp -> {
                val sources = ArrayList<PDDocument>()
                try {
                    op.sources.forEach { sources += open(it) }
                    val pages = sources.flatMapIndexed { d, doc -> (0 until doc.numberOfPages).map { d to it } }
                    val style = op.style
                    val sheetSize = if (style.landscape) PDRectangle(SHEET_A4_HEIGHT, SHEET_A4_WIDTH) else PDRectangle(SHEET_A4_WIDTH, SHEET_A4_HEIGHT)
                    val (columns, rows) = nUpGrid(style.perSheet, style.landscape)
                    val cells = nUpCells(sheetSize.width, sheetSize.height, columns, rows, sheetMargin(style.perSheet), sheetGap(style.perSheet))
                    val sheets = sheetCount(pages.size, style.perSheet)
                    val frame = style.borderWidth.coerceIn(BORDER_MIN, BORDER_MAX)

                    PDDocument(memory()).use { target ->
                        val layers = LayerUtility(target)
                        for (sheetIndex in 0 until sheets) {
                            currentCoroutineContext().ensureActive()
                            progress(sheetIndex, sheets)
                            val sheet = PDPage(sheetSize)
                            target.addPage(sheet)
                            PDPageContentStream(target, sheet).use { cs ->
                                for (slot in 0 until style.perSheet) {
                                    val (d, index) = pages.getOrNull(sheetIndex * style.perSheet + slot) ?: break
                                    val source = sources[d]
                                    val box = source.getPage(index).cropBox
                                    val form = layers.importPageAsForm(source, index)
                                    val put = fitInto(box.width, box.height, cells[slot])
                                    cs.saveGraphicsState()
                                    // Scale the page and move its lower-left corner to the slot.
                                    cs.transform(Matrix(put.scale, 0f, 0f, put.scale, put.x - box.lowerLeftX * put.scale, put.y - box.lowerLeftY * put.scale))
                                    cs.drawForm(form)
                                    cs.restoreGraphicsState()
                                    if (style.border) {
                                        // Drawn just inside the page's edge so thick lines aren't cut off at the sheet's edge.
                                        cs.saveGraphicsState()
                                        cs.setStrokingColor(0f, 0f, 0f)
                                        cs.setLineWidth(frame)
                                        cs.addRect(put.x + frame / 2f, put.y + frame / 2f, put.width - frame, put.height - frame)
                                        cs.stroke()
                                        cs.restoreGraphicsState()
                                    }
                                }
                            }
                        }
                        progress(sheets, sheets)
                        target.save(out)
                    }
                } finally {
                    sources.forEach { runCatching { it.close() } }
                }
            }
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    /** A private copy of the source, so PdfBox can read it with random access whatever kind of Uri it came from. */
    private fun copyToCache(uri: Uri): File {
        val file = File.createTempFile("src", ".pdf", workDir)
        val input = resolver.openInputStream(uri) ?: throw IOException("Can't read the PDF")
        input.use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }
        return file
    }

    /** Checks the saved file and returns its page count (the expected one when the file can't be opened to count). */
    private fun verify(target: Uri, job: PdfJob): Int {
        // Encrypted files can't be opened by Android's renderer, so only the header is checked for them.
        if (job.op is PdfOp.Encrypt) {
            check(runner.looksLikePdf(target)) { "The saved file isn't a valid PDF" }
            return job.pages
        }
        val pages = runner.pageCountOf(target)
        // A job whose page count wasn't known up front (a locked PDF being decrypted) only needs to have some pages.
        check(pages > 0 && (job.pages == 0 || pages == job.pages)) { "The saved file isn't a valid PDF" }
        return pages
    }
}
