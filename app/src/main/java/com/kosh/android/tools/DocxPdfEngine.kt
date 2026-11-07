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
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TabStopSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Word (.docx) to PDF, on the device. The file is read by [DocxParser], drawn page by page with Android's PdfDocument
 * (system fonts, so every script the phone can show works), and then laid out on A4 sheets by [PdfBoxEngine]: one page per
 * sheet or several, portrait or landscape, with an optional frame around every page.
 */
class DocxPdfEngine(context: Context, private val runner: PdfToolsRunner, private val box: PdfBoxEngine) {

    private val resolver = context.contentResolver
    private val workDir = File(context.cacheDir, "docx").apply { mkdirs() }

    /** True when the file is a Word package (a ZIP that holds word/document.xml). */
    suspend fun looksLikeDocx(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val copy = runCatching { copyToCache(uri) }.getOrNull() ?: return@withContext false
        try {
            ZipFile(copy).use { it.getEntry("word/document.xml") != null }
        } catch (e: Exception) {
            false
        } finally {
            copy.delete()
        }
    }

    suspend fun convert(source: Uri, style: NUpStyle, name: String, destination: Uri, onProgress: (Int, Int) -> Unit): Result<List<ToolOutput>> =
        withContext(Dispatchers.IO) {
            val temporary = ArrayList<File>()
            try {
                onProgress(0, 0)
                val copy = copyToCache(source).also { temporary += it }
                val context = currentCoroutineContext()
                val drawn = ZipFile(copy).use { zip ->
                    val model = DocxParser { name -> partStream(zip, name, MAX_XML_BYTES) }.parse()
                    DocxPdfRenderer(model, media = { path -> mediaBytes(zip, path) }, check = { context.ensureActive() }).render(workDir)
                }
                temporary += drawn.files

                val sheets = sheetCount(drawn.pages, style.perSheet)
                val job = PdfJob(name, PdfOp.NUp(drawn.files.map { Uri.fromFile(it) }, style), sheets)
                // Saving into the file the user just created; PdfBoxEngine removes it again if anything fails.
                box.run(listOf(job), ToolDestination.File(destination), onProgress)
            } catch (e: CancellationException) {
                withContext(NonCancellable) { runner.delete(destination) }
                throw e
            } catch (e: Exception) {
                runner.delete(destination)
                Result.failure(if (e is DocxException) IOException(e.message, e) else e)
            } catch (e: OutOfMemoryError) {
                runner.delete(destination)
                Result.failure(IOException("This document is too big to convert on this device"))
            } finally {
                temporary.forEach { it.delete() }
            }
        }

    // ---- package access ------------------------------------------------------------------------------------------------------

    private fun partStream(zip: ZipFile, name: String, limit: Long) =
        zip.getEntry(name)?.takeIf { !it.isDirectory && (it.size < 0 || it.size <= limit) }?.let { zip.getInputStream(it) }

    private fun mediaBytes(zip: ZipFile, path: String): ByteArray? = try {
        partStream(zip, path, MAX_MEDIA_BYTES)?.use { it.readBytes() }
    } catch (e: Exception) {
        null
    }

    private fun copyToCache(uri: Uri): File {
        val file = File.createTempFile("docx", ".zip", workDir)
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Can't read the file")
            input.use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        return file
    }

    private companion object {
        const val MAX_XML_BYTES = 64L * 1024 * 1024
        const val MAX_MEDIA_BYTES = 48L * 1024 * 1024
    }
}

internal class DrawnDocument(val files: List<File>, val pages: Int)

/**
 * Draws a parsed document onto PDF pages. Text is laid out with StaticLayout (line breaking, alignment and shaping done by
 * Android); paragraphs that don't fit are split between pages line by line, table rows move as a whole.
 *
 * Pictures are kept in memory until the PDF holding them has been written, so the pages are written in several small PDFs
 * ("chunks", merged later by the N-up step) whenever the pictures of the current chunk have grown large.
 */
internal class DocxPdfRenderer(
    private val doc: DocxDocument,
    private val media: (String) -> ByteArray?,
    private val check: () -> Unit,
) {
    private val page = doc.page
    private val pageWidth = page.width.roundToInt().coerceAtLeast(100)
    private val pageHeight = page.height.roundToInt().coerceAtLeast(100)
    private val left = page.left
    private val top = page.top
    private val bottom = pageHeight - page.bottom
    private val contentWidth = max(40f, pageWidth - page.left - page.right)

    private var pdf: PdfDocument? = null
    private var current: PdfDocument.Page? = null
    private var canvas: Canvas = Canvas()
    private var y = top
    private var pageCount = 0
    private val chunks = ArrayList<File>()
    private val pendingBitmaps = ArrayList<Bitmap>()
    private var pendingBytes = 0L
    private var workDir: File? = null

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = Color.BLACK }

    fun render(dir: File): DrawnDocument {
        workDir = dir
        try {
            newPage()
            flow(doc.blocks)
            closePage()
            flushChunk()
        } catch (e: Throwable) {
            current?.let { runCatching { pdf?.finishPage(it) } }
            current = null
            runCatching { pdf?.close() }
            pdf = null
            pendingBitmaps.forEach { it.recycle() }
            pendingBitmaps.clear()
            chunks.forEach { it.delete() }
            throw e
        }
        return DrawnDocument(chunks.toList(), pageCount)
    }

    // ---- pages ---------------------------------------------------------------------------------------------------------------------

    private fun newPage() {
        closePage()
        if (pendingBytes > CHUNK_BITMAP_BYTES) flushChunk()
        val document = pdf ?: PdfDocument().also { pdf = it }
        pageCount++
        current = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount).create())
        canvas = current!!.canvas
        y = top
    }

    private fun closePage() {
        current?.let { pdf?.finishPage(it) }
        current = null
    }

    /** Writes the current PDF to a file and lets go of the pictures it used. */
    private fun flushChunk() {
        val document = pdf ?: return
        val file = File.createTempFile("docxpart", ".pdf", workDir)
        chunks += file
        try {
            file.outputStream().buffered().use { document.writeTo(it) }
        } finally {
            document.close()
            pdf = null
            pendingBitmaps.forEach { it.recycle() }
            pendingBitmaps.clear()
            pendingBytes = 0
        }
    }

    private val atTop get() = y <= top + 0.01f

    // ---- flowing blocks down the pages ------------------------------------------------------------------------------------------------

    private fun flow(blocks: List<DocxBlock>) {
        for (block in blocks) {
            check()
            when (block) {
                is DocxParagraph -> flowParagraph(block)
                is DocxTable -> flowTable(block)
            }
        }
    }

    private fun flowParagraph(p: DocxParagraph) {
        if (p.pageBreakBefore && !atTop) newPage()
        val pieces = pieces(p, contentWidth)
        pieces.forEachIndexed { i, piece ->
            val before = if (i == 0) p.before else 0f
            val after = if (i == pieces.lastIndex) p.after else 0f
            when (piece) {
                is TextPiece -> placeText(piece, before, after)
                is ImagePiece -> placeImage(piece, before, after)
                PageBreakPiece -> newPage()
            }
        }
    }

    private fun placeText(piece: TextPiece, before: Float, after: Float) {
        val layout = piece.layout
        val lines = layout.lineCount
        var from = 0
        while (from < lines) {
            val gapBefore = if (from == 0 && !atTop) before else 0f
            val room = bottom - y - gapBefore
            var to = from
            while (to < lines && layout.getLineBottom(to) - layout.getLineTop(from) <= room) to++
            if (to == from) {
                // Not even one line fits: start a new page, unless this already is one (then draw it anyway).
                if (!atTop) { newPage(); continue }
                to = from + 1
            }
            val height = (layout.getLineBottom(to - 1) - layout.getLineTop(from)).toFloat()
            drawLines(layout, from, to, left + piece.x, y + gapBefore)
            y += gapBefore + height
            from = to
            if (from < lines) newPage()
        }
        y += after
    }

    private fun placeImage(piece: ImagePiece, before: Float, after: Float) {
        val size = imageSize(piece, contentWidth - piece.x, bottom - top) ?: return
        val (w, h) = size
        var gap = if (atTop) 0f else before
        if (!atTop && y + gap + h > bottom) { newPage(); gap = 0f }
        val x = left + piece.x + when (piece.align) {
            DocxAlign.CENTER -> (contentWidth - piece.x - w) / 2f
            DocxAlign.RIGHT -> contentWidth - piece.x - w
            else -> 0f
        }
        drawImage(piece.path, x, y + gap, w, h)
        y += gap + h + after
    }

    // ---- tables ---------------------------------------------------------------------------------------------------------------------------

    private fun flowTable(t: DocxTable) {
        for (row in measureTable(t, contentWidth)) {
            check()
            if (!atTop && y + row.height > bottom) newPage()
            row.draw(left, y)
            y += row.height
        }
        y += TABLE_GAP
    }

    /** Every row of a table, measured for a table [width] wide, ready to draw. */
    private fun measureTable(t: DocxTable, width: Float): List<Measured> {
        val columns = columnWidths(t, width)
        return t.rows.map { row ->
            var column = 0
            var x = 0f
            val cells = ArrayList<Triple<Float, Float, List<Measured>>>() // x, width, content
            var contentHeight = 0f
            for (cell in row) {
                val span = min(cell.span, max(1, columns.size - column))
                val cellWidth = (column until column + span).sumOf { columns.getOrElse(it) { columns.lastOrNull() ?: width }.toDouble() }.toFloat()
                val inner = measureBlocks(cell.blocks, max(20f, cellWidth - 2 * CELL_PAD_X))
                contentHeight = max(contentHeight, inner.sumOf { it.height.toDouble() }.toFloat())
                cells += Triple(x, cellWidth, inner)
                x += cellWidth
                column += span
            }
            val height = contentHeight + 2 * CELL_PAD_Y
            Measured(height) { ox, oy ->
                for ((cx, cw, content) in cells) {
                    var cy = oy + CELL_PAD_Y
                    for (item in content) { item.draw(ox + cx + CELL_PAD_X, cy); cy += item.height }
                    if (t.borders) canvas.drawRect(ox + cx, oy, ox + cx + cw, oy + height, borderPaint)
                }
            }
        }
    }

    private fun columnWidths(t: DocxTable, width: Float): List<Float> {
        val count = max(t.grid.size, t.rows.maxOfOrNull { row -> row.sumOf { it.span } } ?: 1)
        val grid = if (t.grid.size == count && t.grid.all { it > 0f }) t.grid else List(count) { width / count }
        val total = grid.sum()
        return if (total > width) grid.map { it * width / total } else grid
    }

    // ---- content that is drawn later (cells and nested tables) ------------------------------------------------------------------------

    /** Something with a known height that can be drawn with its top-left corner at (x, y). */
    private class Measured(val height: Float, val draw: (x: Float, y: Float) -> Unit)

    private fun measureBlocks(blocks: List<DocxBlock>, width: Float): List<Measured> {
        val out = ArrayList<Measured>()
        for (block in blocks) {
            when (block) {
                is DocxParagraph -> {
                    val pieces = pieces(block, width)
                    pieces.forEachIndexed { i, piece ->
                        val before = if (i == 0) block.before else 0f
                        val after = if (i == pieces.lastIndex) block.after else 0f
                        when (piece) {
                            is TextPiece -> out += Measured(before + piece.layout.height + after) { x, y ->
                                drawLines(piece.layout, 0, piece.layout.lineCount, x + piece.x, y + before)
                            }
                            is ImagePiece -> imageSize(piece, width - piece.x, bottom - top)?.let { (w, h) ->
                                out += Measured(before + h + after) { x, y ->
                                    val dx = when (piece.align) {
                                        DocxAlign.CENTER -> (width - piece.x - w) / 2f
                                        DocxAlign.RIGHT -> width - piece.x - w
                                        else -> 0f
                                    }
                                    drawImage(piece.path, x + piece.x + dx, y + before, w, h)
                                }
                            }
                            PageBreakPiece -> Unit // a page break inside a table cell is ignored
                        }
                    }
                }
                is DocxTable -> {
                    val rows = measureTable(block, width)
                    out += Measured(rows.sumOf { it.height.toDouble() }.toFloat() + TABLE_GAP) { x, y ->
                        var ry = y
                        for (row in rows) { row.draw(x, ry); ry += row.height }
                    }
                }
            }
        }
        return out
    }

    // ---- text -----------------------------------------------------------------------------------------------------------------------------

    private sealed interface Piece
    private class TextPiece(val layout: StaticLayout, val x: Float) : Piece
    private class ImagePiece(val path: String, val width: Float, val height: Float, val x: Float, val align: DocxAlign) : Piece
    private data object PageBreakPiece : Piece

    /** Cuts a paragraph into text, pictures and page breaks, in the order they appear. */
    private fun pieces(p: DocxParagraph, width: Float): List<Piece> {
        val out = ArrayList<Piece>()
        val run = ArrayList<DocxInline.Text>()
        val hasOthers = p.inlines.any { it !is DocxInline.Text }
        // Where the text box starts, so that a hanging indent never needs a negative margin.
        val boxLeft = max(0f, p.indentLeft + min(0f, p.firstLine))
        val boxWidth = max(20f, width - boxLeft - p.indentRight)

        fun flushText() {
            val joined = run.joinToString("") { it.text }
            // A paragraph that only wraps a picture shouldn't also produce an empty line of text.
            if (run.isNotEmpty() && !(joined.isBlank() && hasOthers && p.labelTab == null)) {
                out += TextPiece(textLayout(p, run, boxWidth, boxLeft), boxLeft)
            }
            run.clear()
        }
        for (inline in p.inlines) {
            when (inline) {
                is DocxInline.Text -> run += inline
                is DocxInline.Image -> { flushText(); out += ImagePiece(inline.path, inline.widthPt, inline.heightPt, p.indentLeft, p.align) }
                DocxInline.PageBreak -> { flushText(); out += PageBreakPiece }
            }
        }
        flushText()
        if (out.isEmpty()) out += TextPiece(textLayout(p, emptyList(), boxWidth, boxLeft), boxLeft)
        return out
    }

    private fun textLayout(p: DocxParagraph, runs: List<DocxInline.Text>, width: Float, boxLeft: Float): StaticLayout {
        val text = SpannableStringBuilder()
        val base = p.markSize.coerceAtLeast(1f)
        for (r in runs) {
            if (r.text.isEmpty()) continue
            val start = text.length
            text.append(r.text)
            val end = text.length
            val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            val face = when {
                r.bold && r.italic -> Typeface.BOLD_ITALIC
                r.bold -> Typeface.BOLD
                r.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (face != Typeface.NORMAL) text.setSpan(StyleSpan(face), start, end, flags)
            text.setSpan(
                TypefaceSpan(when (r.font) { DocxFont.SERIF -> "serif"; DocxFont.MONO -> "monospace"; DocxFont.SANS -> "sans-serif" }),
                start, end, flags,
            )
            // Sizes are relative to the paragraph's base size so half-points (10.5 pt) stay exact.
            if (r.sizePt != base) text.setSpan(RelativeSizeSpan(r.sizePt / base), start, end, flags)
            if (r.underline) text.setSpan(UnderlineSpan(), start, end, flags)
            if (r.strike) text.setSpan(StrikethroughSpan(), start, end, flags)
            r.color?.let { text.setSpan(ForegroundColorSpan(it), start, end, flags) }
            if (r.script != 0) {
                text.setSpan(if (r.script > 0) SuperscriptSpan() else SubscriptSpan(), start, end, flags)
                text.setSpan(RelativeSizeSpan(0.7f), start, end, flags)
            }
        }
        // Margins measured from the text box: where the first line starts and where the following lines start.
        val rest = max(0f, p.indentLeft - boxLeft).roundToInt()
        val first = max(0f, p.indentLeft + p.firstLine - boxLeft).roundToInt()
        if (text.isNotEmpty()) {
            text.setSpan(LeadingMarginSpan.Standard(first, rest), 0, text.length, Spanned.SPAN_PARAGRAPH)
            p.labelTab?.let { text.setSpan(TabStopSpan.Standard(max(0f, it - boxLeft).roundToInt()), 0, text.length, Spanned.SPAN_PARAGRAPH) }
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = p.markSize; color = Color.BLACK }
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, floor(width).toInt().coerceAtLeast(20))
            .setIncludePad(false)
            .setLineSpacing(0f, p.lineMultiplier)
            .setAlignment(
                when (p.align) {
                    DocxAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
                    DocxAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_NORMAL
                },
            )
        if (p.align == DocxAlign.JUSTIFY) builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        return builder.build()
    }

    /** Draws lines [from, to) of [layout] with the top of line [from] at (x, y). */
    private fun drawLines(layout: StaticLayout, from: Int, to: Int, x: Float, y: Float) {
        val lineTop = layout.getLineTop(from).toFloat()
        val lineBottom = layout.getLineBottom(to - 1).toFloat()
        canvas.save()
        canvas.translate(x, y - lineTop)
        canvas.clipRect(-2f, lineTop, layout.width + 2f, lineBottom)
        layout.draw(canvas)
        canvas.restore()
    }

    // ---- pictures ------------------------------------------------------------------------------------------------------------------------

    private val sizes = HashMap<String, Pair<Int, Int>?>()

    /** Pixel size of a picture in the package, or null if Android can't read this kind of picture (EMF, WMF...). */
    private fun pixelSize(path: String): Pair<Int, Int>? = sizes.getOrPut(path) {
        val bytes = media(path) ?: return@getOrPut null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth to bounds.outHeight else null
    }

    /** The size in points a picture is drawn at: its size in the document, shrunk to fit the room it has. Null if unreadable. */
    private fun imageSize(piece: ImagePiece, maxWidth: Float, maxHeight: Float): Pair<Float, Float>? {
        val pixels = pixelSize(piece.path) ?: return null
        var w = piece.width
        var h = piece.height
        if (w <= 0f || h <= 0f) { w = pixels.first * 0.75f; h = pixels.second * 0.75f } // 96 pixels to the inch
        val scale = min(1f, min(max(20f, maxWidth) / w, max(20f, maxHeight) / h))
        return (w * scale) to (h * scale)
    }

    private fun drawImage(path: String, x: Float, y: Float, w: Float, h: Float) {
        val bitmap = decode(path, (max(w, h) * 2f).roundToInt().coerceIn(64, MAX_IMAGE_PX)) ?: return
        pendingBitmaps += bitmap
        pendingBytes += bitmap.byteCount
        canvas.drawBitmap(bitmap, null, RectF(x, y, x + w, y + h), imagePaint)
    }

    /** Decodes a picture at about [targetPx] on its longer side (about 140 dpi): enough for a sharp page, light on memory. */
    private fun decode(path: String, targetPx: Int): Bitmap? {
        val bytes = media(path) ?: return null
        val size = pixelSize(path) ?: return null
        var sample = 1
        while (max(size.first, size.second) / (sample * 2) >= targetPx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private companion object {
        const val CELL_PAD_X = 5f
        const val CELL_PAD_Y = 3f
        const val TABLE_GAP = 6f
        const val MAX_IMAGE_PX = 1800
        const val CHUNK_BITMAP_BYTES = 40L * 1024 * 1024
    }
}
