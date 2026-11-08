package com.kosh.android.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns a Word, Excel or PowerPoint file into one PDF, fully on the device. The Home tab opens the result in the normal PDF
 * reader and "Save as PDF" keeps it next to the original.
 *
 * - Word: [DocxPdfRenderer] (the same one the Word to PDF tool uses), pages merged into one file.
 * - Excel: each visible sheet drawn as a table on A4 landscape pages (wide sheets are split into column bands).
 * - PowerPoint: one page per slide, in the slide's own size.
 */
class OfficePdfConverter(context: Context) {

    private val workDir = File(context.cacheDir, "office").apply { mkdirs() }

    /** Converts [source] into [target] (replaced if it exists). On any failure nothing is left at [target]. */
    suspend fun convert(source: File, kind: FileKind, target: File): Result<File> = withContext(Dispatchers.IO) {
        val temporary = ArrayList<File>()
        try {
            val context = currentCoroutineContext()
            val check = { context.ensureActive() }
            val parts = ZipFile(source).use { zip ->
                val read = { name: String -> zip.getEntry(name)?.takeIf { !it.isDirectory && (it.size < 0 || it.size <= MAX_XML) }?.let { zip.getInputStream(it) } }
                val media = { path: String ->
                    try {
                        zip.getEntry(path)?.takeIf { !it.isDirectory && (it.size < 0 || it.size <= MAX_MEDIA) }?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
                    } catch (e: Exception) {
                        null
                    }
                }
                when (kind) {
                    FileKind.DOCX -> DocxPdfRenderer(DocxParser(read).parse(), media, check).render(workDir).files
                    FileKind.XLSX -> SheetPdfRenderer(XlsxParser(read).parse(), workDir, check).render()
                    FileKind.PPTX -> SlidePdfRenderer(PptxParser(read).parse(), media, workDir, check).render()
                    FileKind.PDF -> throw IOException("Already a PDF")
                }
            }
            temporary += parts
            merge(parts, target)
            Result.success(target)
        } catch (e: CancellationException) {
            target.delete()
            throw e
        } catch (e: DocxException) {
            target.delete()
            Result.failure(IOException(e.message, e))
        } catch (e: Exception) {
            target.delete()
            Result.failure(e)
        } catch (e: OutOfMemoryError) {
            target.delete()
            Result.failure(IOException("This file is too big to convert on this device"))
        } finally {
            temporary.forEach { it.delete() }
        }
    }

    private fun merge(parts: List<File>, target: File) {
        if (parts.isEmpty()) throw IOException("Nothing to convert")
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile ?: workDir, target.name + ".part")
        try {
            if (parts.size == 1) {
                parts[0].copyTo(partial, overwrite = true)
            } else {
                val merger = PDFMergerUtility()
                parts.forEach { merger.addSource(it) }
                partial.outputStream().buffered().use { out ->
                    merger.destinationStream = out
                    merger.mergeDocuments(MemoryUsageSetting.setupMixed(24L * 1024 * 1024).setTempDir(workDir))
                }
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
            }
        } finally {
            partial.delete()
        }
    }

    /**
     * A PDF to show for [source]: converted once and kept in the cache (in a folder of its own so the reader shows the
     * original name), reused until the original changes.
     */
    suspend fun viewable(source: File, kind: FileKind): Result<File> {
        val key = "view-${source.absolutePath.hashCode().toUInt()}-${source.lastModified()}-${source.length()}"
        val target = File(File(workDir, key), uniquePdfName(source.name) { false })
        if (target.length() > 0) {
            target.parentFile?.setLastModified(System.currentTimeMillis())
            rememberOrigin(target, source)
            return Result.success(target)
        }
        return convert(source, kind, target).also { if (it.isSuccess) rememberOrigin(target, source) }
    }

    /** Notes which Office file a cached PDF came from, so a doodle made on it can be saved beside that file. */
    private fun rememberOrigin(pdf: File, source: File) {
        runCatching { File(pdf.parentFile, ORIGIN_FILE).writeText(source.absolutePath) }
    }

    /** Deletes cached conversions that haven't been used for a day. */
    fun prune(olderThanMillis: Long = 24L * 60 * 60 * 1000) {
        val limit = System.currentTimeMillis() - olderThanMillis
        workDir.listFiles()?.forEach { if (it.lastModified() < limit) it.deleteRecursively() }
    }

    companion object {
        const val ORIGIN_FILE = "origin.txt"
        const val MAX_XML = 64L * 1024 * 1024
        const val MAX_MEDIA = 48L * 1024 * 1024
    }
}

/**
 * Writes pages of any size into PDF files, starting a new file whenever the pictures waiting to be written get big, so a
 * deck full of photos never holds them all in memory.
 */
internal class ChunkedPdf(private val dir: File, private val check: () -> Unit) {
    private var pdf: PdfDocument? = null
    private var page: PdfDocument.Page? = null
    private var count = 0
    private var pending = 0L
    private val bitmaps = ArrayList<Bitmap>()
    val files = ArrayList<File>()

    fun start(width: Int, height: Int): Canvas {
        check()
        finishPage()
        if (pending > CHUNK_BYTES) flush()
        val doc = pdf ?: PdfDocument().also { pdf = it }
        count++
        page = doc.startPage(PdfDocument.PageInfo.Builder(width.coerceAtLeast(50), height.coerceAtLeast(50), count).create())
        return page!!.canvas
    }

    /** Keeps [bitmap] alive until the file that uses it is written. */
    fun hold(bitmap: Bitmap) { bitmaps += bitmap; pending += bitmap.byteCount }

    private fun finishPage() { page?.let { pdf?.finishPage(it) }; page = null }

    private fun flush() {
        val doc = pdf ?: return
        val file = File.createTempFile("officepart", ".pdf", dir)
        files += file
        try {
            file.outputStream().buffered().use { doc.writeTo(it) }
        } finally {
            doc.close()
            pdf = null
            bitmaps.forEach { it.recycle() }
            bitmaps.clear()
            pending = 0
        }
    }

    fun finish(): List<File> {
        finishPage()
        flush()
        return files.toList()
    }

    fun abort() {
        page?.let { runCatching { pdf?.finishPage(it) } }
        page = null
        runCatching { pdf?.close() }
        pdf = null
        bitmaps.forEach { it.recycle() }
        bitmaps.clear()
        files.forEach { it.delete() }
        files.clear()
    }

    private companion object { const val CHUNK_BYTES = 40L * 1024 * 1024 }
}

// ---- Excel -------------------------------------------------------------------------------------------------------------------------------

internal class SheetPdfRenderer(private val book: XlsxWorkbook, private val dir: File, private val check: () -> Unit) {

    private val pageW = 842
    private val pageH = 595
    private val margin = 28f
    private val titleHeight = 22f

    private val grid = Paint().apply { color = 0xFFBFBFBF.toInt(); style = Paint.Style.STROKE; strokeWidth = 0.5f }
    private val fillPaint = Paint()
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = FONT }
    private val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; typeface = Typeface.DEFAULT_BOLD; color = Color.DKGRAY }

    fun render(): List<File> {
        val out = ChunkedPdf(dir, check)
        try {
            for (sheet in book.sheets) draw(sheet, out)
            return out.finish()
        } catch (e: Throwable) {
            out.abort()
            throw e
        }
    }

    private fun draw(sheet: XlsxSheet, out: ChunkedPdf) {
        val columns = (sheet.rows.maxOfOrNull { r -> r.cells.maxOfOrNull { it.col } ?: 0 } ?: 0).coerceAtMost(MAX_COLUMNS - 1)
        fun widthOf(c: Int) = sheet.colWidths[c] ?: sheet.defaultColWidth
        val visible = (0..columns).filter { widthOf(it) > 0f }
        if (visible.isEmpty()) { out.start(pageW, pageH).drawText(sheet.name, margin, margin + 12f, title); return }

        // Shrink a little so a slightly-too-wide sheet still fits, then split what is still too wide into bands.
        val room = pageW - 2 * margin
        val total = visible.sumOf { widthOf(it).toDouble() }.toFloat()
        val scale = if (total > room) max(0.7f, room / total) else 1f
        val bands = ArrayList<List<Int>>()
        var band = ArrayList<Int>()
        var used = 0f
        for (c in visible) {
            val w = widthOf(c) * scale
            if (band.isNotEmpty() && used + w > room) { bands += band; band = ArrayList(); used = 0f }
            band += c
            used += w
        }
        if (band.isNotEmpty()) bands += band

        for ((bandIndex, cols) in bands.withIndex()) {
            var canvas = out.start(pageW, pageH)
            var y = margin
            fun header() {
                val label = if (bands.size > 1) "${sheet.name} (${bandIndex + 1}/${bands.size})" else sheet.name
                canvas.drawText(label, margin, y + 12f, title)
                y += titleHeight
            }
            header()
            for (row in sheet.rows) {
                check()
                val height = ((row.height ?: sheet.defaultRowHeight) * scale).coerceIn(6f, 200f)
                if (y + height > pageH - margin) { canvas = out.start(pageW, pageH); y = margin; header() }
                val byCol = row.cells.associateBy { it.col }
                var x = margin
                for (c in cols) {
                    val w = widthOf(c) * scale
                    val cell = byCol[c]
                    if (cell?.fill != null) { fillPaint.color = cell.fill; canvas.drawRect(x, y, x + w, y + height, fillPaint) }
                    canvas.drawRect(x, y, x + w, y + height, grid)
                    if (cell != null && cell.text.isNotEmpty()) drawCell(canvas, cell, x, y, w, height, scale)
                    x += w
                }
                y += height
            }
        }
    }

    private fun drawCell(canvas: Canvas, cell: XlsxCell, x: Float, y: Float, w: Float, h: Float, scale: Float) {
        text.textSize = FONT * scale
        text.typeface = if (cell.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        text.color = cell.color ?: Color.BLACK
        val pad = 2.5f
        val line = cell.text.replace('\n', ' ')
        val shown = TextUtils.ellipsize(line, text, max(1f, w - 2 * pad), TextUtils.TruncateAt.END).toString()
        val measured = text.measureText(shown)
        val align = if (cell.align != 0) cell.align else if (cell.numeric) 3 else 1
        val tx = when (align) { 2 -> x + (w - measured) / 2f; 3 -> x + w - pad - measured; else -> x + pad }
        val baseline = y + h / 2f - (text.ascent() + text.descent()) / 2f
        canvas.drawText(shown, tx, baseline, text)
    }

    private companion object {
        const val FONT = 10f
        const val MAX_COLUMNS = 200
    }
}

// ---- PowerPoint --------------------------------------------------------------------------------------------------------------------------

internal class SlidePdfRenderer(
    private val deck: PptxDeck,
    private val media: (String) -> ByteArray?,
    private val dir: File,
    private val check: () -> Unit,
) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val gridLine = Paint().apply { color = 0xFF808080.toInt(); style = Paint.Style.STROKE; strokeWidth = 0.6f }

    fun render(): List<File> {
        val out = ChunkedPdf(dir, check)
        try {
            for (slide in deck.slides) {
                val canvas = out.start(deck.width.roundToInt(), deck.height.roundToInt())
                fill.color = slide.background ?: Color.WHITE
                canvas.drawRect(0f, 0f, deck.width, deck.height, fill)
                for (shape in slide.shapes) {
                    check()
                    when (shape) {
                        is PptxShape.Block -> block(canvas, shape)
                        is PptxShape.Picture -> picture(canvas, out, shape)
                        is PptxShape.Table -> table(canvas, shape)
                    }
                }
            }
            return out.finish()
        } catch (e: Throwable) {
            out.abort()
            throw e
        }
    }

    private fun block(canvas: Canvas, s: PptxShape.Block) {
        val b = s.box
        s.fill?.let { fill.color = it; canvas.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, fill) }
        s.line?.let { stroke.color = it; canvas.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, stroke) }
        if (s.paragraphs.isEmpty()) return
        val innerW = max(10f, b.w - 2 * INSET_X)
        val innerH = max(10f, b.h - 2 * INSET_Y)
        // If the text doesn't fit, shrink it step by step (PowerPoint's "shrink text on overflow").
        var factor = 1f
        var layout = layout(s, innerW, factor)
        while (layout.height > innerH && factor > 0.4f) { factor -= 0.1f; layout = layout(s, innerW, factor) }
        val top = if (s.middle) b.y + INSET_Y + max(0f, (innerH - layout.height) / 2f) else b.y + INSET_Y
        canvas.save()
        canvas.clipRect(b.x, b.y, b.x + b.w, b.y + b.h + 2f)
        canvas.translate(b.x + INSET_X, top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun layout(s: PptxShape.Block, width: Float, factor: Float): StaticLayout {
        val sb = SpannableStringBuilder()
        var alignment = Layout.Alignment.ALIGN_NORMAL
        for ((i, p) in s.paragraphs.withIndex()) {
            if (i > 0) sb.append('\n')
            if (i == 0) alignment = when (p.align) { 1 -> Layout.Alignment.ALIGN_CENTER; 2 -> Layout.Alignment.ALIGN_OPPOSITE; else -> alignment }
            val start = sb.length
            if (p.bullet) sb.append("   ".repeat(p.level)).append("• ")
            for (r in p.runs) {
                val from = sb.length
                sb.append(r.text)
                val to = sb.length
                if (to == from) continue
                val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                sb.setSpan(AbsoluteSizeSpan(((r.size ?: DEFAULT_SIZE) * factor).roundToInt().coerceAtLeast(4), false), from, to, flags)
                val face = if (r.bold && r.italic) Typeface.BOLD_ITALIC else if (r.bold) Typeface.BOLD else if (r.italic) Typeface.ITALIC else Typeface.NORMAL
                if (face != Typeface.NORMAL) sb.setSpan(StyleSpan(face), from, to, flags)
                r.color?.let { sb.setSpan(ForegroundColorSpan(it), from, to, flags) }
            }
            if (sb.length == start) { // an empty paragraph still takes up a line
                sb.append(' ')
                sb.setSpan(AbsoluteSizeSpan(((DEFAULT_SIZE) * factor).roundToInt(), false), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = DEFAULT_SIZE * factor; color = Color.BLACK }
        return StaticLayout.Builder.obtain(sb, 0, sb.length, paint, floor(width).toInt().coerceAtLeast(10))
            .setIncludePad(false)
            .setAlignment(alignment)
            .build()
    }

    private fun picture(canvas: Canvas, out: ChunkedPdf, s: PptxShape.Picture) {
        val bytes = media(s.path) ?: return
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return // EMF/WMF and similar: not readable by Android
        val target = (max(s.box.w, s.box.h) * 2f).roundToInt().coerceIn(64, MAX_IMAGE_PX)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        val bitmap = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: OutOfMemoryError) {
            null
        } ?: return
        out.hold(bitmap)
        canvas.drawBitmap(bitmap, null, RectF(s.box.x, s.box.y, s.box.x + s.box.w, s.box.y + s.box.h), image)
    }

    private fun table(canvas: Canvas, t: PptxShape.Table) {
        val columns = t.rows.maxOf { it.size }.coerceAtLeast(1)
        val widths = if (t.colWidths.size == columns && t.colWidths.all { it > 0 }) t.colWidths else List(columns) { t.box.w / columns }
        val rowHeight = t.box.h / t.rows.size
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = TABLE_SIZE; color = Color.BLACK }
        var y = t.box.y
        for (row in t.rows) {
            var x = t.box.x
            for (c in 0 until columns) {
                val w = widths[c]
                canvas.drawRect(x, y, x + w, y + rowHeight, gridLine)
                row.getOrNull(c)?.takeIf { it.isNotEmpty() }?.let { cell ->
                    val line = TextUtils.ellipsize(cell.replace('\n', ' '), paint, max(1f, w - 6f), TextUtils.TruncateAt.END).toString()
                    canvas.drawText(line, x + 3f, y + rowHeight / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
                }
                x += w
            }
            y += rowHeight
        }
    }

    private companion object {
        const val INSET_X = 7.2f
        const val INSET_Y = 3.6f
        const val DEFAULT_SIZE = 18f
        const val TABLE_SIZE = 12f
        const val MAX_IMAGE_PX = 1800
    }
}
