package com.kosh.android.notes.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import com.kosh.android.notes.format.ImagePiece
import com.kosh.android.notes.format.MdBlank
import com.kosh.android.notes.format.MdBlock
import com.kosh.android.notes.format.NoteImageTokens
import com.kosh.android.notes.format.TextPiece
import com.kosh.android.notes.format.MdBullet
import com.kosh.android.notes.format.MdChecklist
import com.kosh.android.notes.format.MdHeading
import com.kosh.android.notes.format.MdNumbered
import com.kosh.android.notes.format.MdParagraph
import com.kosh.android.notes.format.Span
import com.kosh.android.notes.format.parseMarkdown
import java.io.File
import java.io.OutputStream
import kotlin.math.min

/**
 * Writes a note as a multi-page A4 PDF with real text (not an image): wrapped paragraphs, headings,
 * bullets, numbers and drawn checkboxes. Fonts fall back through Android's system fonts, so common
 * non-Latin scripts render; colour emoji may not.
 */
class PdfNoteWriter {

    fun write(title: String, content: String, images: List<File>, out: OutputStream) {
        val doc = PdfDocument()
        try {
            val writer = PageWriter(doc)
            writer.newPage()
            val cleanTitle = title.replace(Regex("\\s+"), " ").trim()
            if (cleanTitle.isNotEmpty()) {
                writer.paragraph(listOf(Span(cleanTitle)), size = 22f, bold = true, indent = 0f)
                writer.space(10f)
            }
            val byName = images.associateBy { it.name }
            val pieces = NoteImageTokens.split(content)
            val lastText = pieces.indexOfLast { it is TextPiece }
            pieces.forEachIndexed { index, piece ->
                when (piece) {
                    is ImagePiece -> byName[piece.name]?.let { writer.image(it) }
                    is TextPiece -> {
                        var blocks = parseMarkdown(piece.text)
                        if (index == lastText) blocks = blocks.dropLastWhile { it is MdBlank }
                        for (block in blocks) writeBlock(writer, block)
                    }
                }
            }
            // Pictures the text doesn't mention (shouldn't happen) still go at the end rather than being lost.
            val referenced = NoteImageTokens.referencedNames(content)
            val loose = images.filter { it.name !in referenced }
            if (loose.isNotEmpty()) {
                writer.space(12f)
                loose.forEach { writer.image(it) }
            }
            writer.finish()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun writeBlock(writer: PageWriter, block: MdBlock) {
        when (block) {
            is MdBlank -> writer.space(BODY * 0.8f)
            is MdHeading -> {
                val size = when (block.level) { 1 -> 18.5f; 2 -> 16f; else -> 14f }
                writer.space(size * 0.3f)
                writer.paragraph(block.spans, size = size, bold = true, indent = 0f)
                writer.space(size * 0.2f)
            }
            is MdParagraph -> writer.paragraph(block.spans, size = BODY, bold = false, indent = 0f)
            is MdBullet -> writer.listItem(block.spans, block.level, Marker.Bullet)
            is MdNumbered -> writer.listItem(block.spans, block.level, Marker.Number(block.number))
            is MdChecklist -> writer.listItem(block.spans, block.level, Marker.Check(block.checked))
        }
    }

    private sealed interface Marker {
        data object Bullet : Marker
        data class Number(val n: Int) : Marker
        data class Check(val checked: Boolean) : Marker
    }

    private class PageWriter(private val doc: PdfDocument) {
        private val pageW = 595
        private val pageH = 842
        private val margin = 48f
        private val contentW = pageW - 2 * margin
        private val bottom = pageH - margin
        private var pageNumber = 0
        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = margin

        private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f; color = Color.GRAY; textAlign = Paint.Align.CENTER
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = Color.BLACK; strokeWidth = 1f
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

        fun newPage() {
            if (pageNumber > 0) finishPage()
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create())
            canvas = page.canvas
            y = margin
        }

        private fun finishPage() {
            canvas.drawText(pageNumber.toString(), pageW / 2f, pageH - 24f, numberPaint)
            doc.finishPage(page)
        }

        fun finish() {
            if (pageNumber > 0) finishPage()
        }

        /** Draws an attached image at the page width (or smaller to fit the page), moving to a new page if needed. */
        fun image(file: File) {
            val bmp = decodeForPdf(file) ?: return
            try {
                val scale = min(contentW / bmp.width, (bottom - margin) / bmp.height)
                val w = bmp.width * scale
                val h = bmp.height * scale
                if (y + h > bottom && y > margin) newPage()
                val rect = RectF(margin, y, margin + w, y + h)
                canvas.drawBitmap(bmp, null, rect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                y += h + 12f
            } finally {
                bmp.recycle()
            }
        }

        private fun decodeForPdf(file: File): Bitmap? = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1200) sample *= 2 // about 1200 px wide keeps the PDF small
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: OutOfMemoryError) {
            null
        }

        fun space(amount: Float) {
            y += amount
            if (y > bottom) newPage()
        }

        fun paragraph(spans: List<Span>, size: Float, bold: Boolean, indent: Float) {
            val layout = buildLayout(spans, size, bold, contentW - indent)
            drawLayout(layout, margin + indent, marker = null)
            y += size * 0.35f
        }

        fun listItem(spans: List<Span>, level: Int, marker: Marker) {
            val levelIndent = level * 18f
            val textIndent = levelIndent + 20f
            val layout = buildLayout(spans, BODY, false, contentW - textIndent)
            drawLayout(layout, margin + textIndent) { baseline ->
                drawMarker(marker, margin + levelIndent, margin + textIndent, baseline)
            }
            y += BODY * 0.35f
        }

        private fun drawMarker(marker: Marker, x: Float, textX: Float, baseline: Float) {
            when (marker) {
                Marker.Bullet -> canvas.drawCircle(x + 5f, baseline - BODY * 0.3f, 2.2f, fillPaint)
                is Marker.Number -> {
                    val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = BODY; color = Color.BLACK; textAlign = Paint.Align.RIGHT }
                    canvas.drawText("${marker.n}.", textX - 5f, baseline, p)
                }
                is Marker.Check -> {
                    val side = BODY * 0.85f
                    val top = baseline - side
                    canvas.drawRect(x, top, x + side, baseline, strokePaint)
                    if (marker.checked) {
                        val tick = Path().apply {
                            moveTo(x + side * 0.2f, top + side * 0.55f)
                            lineTo(x + side * 0.42f, top + side * 0.78f)
                            lineTo(x + side * 0.82f, top + side * 0.22f)
                        }
                        canvas.drawPath(tick, strokePaint.apply { strokeWidth = 1.8f })
                        strokePaint.strokeWidth = 1f
                    }
                }
            }
        }

        private fun buildLayout(spans: List<Span>, size: Float, bold: Boolean, width: Float): StaticLayout {
            val sb = SpannableStringBuilder()
            for (span in spans) {
                val start = sb.length
                sb.append(span.text)
                val end = sb.length
                if (start == end) continue
                val style = when {
                    span.bold && span.italic -> Typeface.BOLD_ITALIC
                    span.bold -> Typeface.BOLD
                    span.italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                if (style != Typeface.NORMAL && !(bold && style == Typeface.BOLD)) {
                    sb.setSpan(StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (span.strike) sb.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (span.code) {
                    sb.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(BackgroundColorSpan(0xFFEEEEEE.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                color = Color.BLACK
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            return StaticLayout.Builder.obtain(sb, 0, sb.length, paint, width.toInt().coerceAtLeast(60))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(false)
                .build()
        }

        /** Draws [layout] across as many pages as it needs, splitting between lines. */
        private fun drawLayout(layout: StaticLayout, x: Float, marker: ((baseline: Float) -> Unit)?) {
            if (y + layout.getLineBottom(0) > bottom && y > margin) newPage()
            marker?.invoke(y + layout.getLineBaseline(0))
            var startLine = 0
            while (startLine < layout.lineCount) {
                val available = bottom - y
                var endLine = startLine
                while (endLine < layout.lineCount &&
                    layout.getLineBottom(endLine) - layout.getLineTop(startLine) <= available
                ) endLine++
                if (endLine == startLine) {
                    if (y > margin + 0.5f) { newPage(); continue }
                    endLine = startLine + 1 // a single line taller than a page: draw it anyway
                }
                val top = layout.getLineTop(startLine).toFloat()
                val lineBottom = layout.getLineBottom(endLine - 1).toFloat()
                canvas.save()
                canvas.translate(x, y - top)
                canvas.clipRect(0f, top, contentW, lineBottom)
                layout.draw(canvas)
                canvas.restore()
                y += lineBottom - top
                startLine = endLine
                if (startLine < layout.lineCount) newPage()
            }
        }
    }

    private companion object {
        const val BODY = 11.5f
    }
}
