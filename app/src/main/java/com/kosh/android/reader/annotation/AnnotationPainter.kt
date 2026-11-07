package com.kosh.android.reader.annotation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.kosh.android.data.db.AnnotationType
import com.kosh.android.data.db.PdfAnnotation
import com.kosh.android.util.AnnotationCodec
import com.kosh.android.util.DrawItem
import kotlin.math.max
import kotlin.math.min

fun PdfAnnotation.toDrawItem() = DrawItem(
    id = id,
    type = annotationType,
    colorArgb = colorArgb,
    size = strokeWidth,
    points = AnnotationCodec.decode(points),
    note = note,
)

/**
 * Draws annotations onto an Android canvas. The same code paints the on-screen overlay and the pages of an
 * exported annotated copy, so both look identical. Coordinates are normalised page space (0..1).
 */
object AnnotationPainter {

    const val HIGHLIGHT_ALPHA = 0x66

    fun draw(canvas: Canvas, items: List<DrawItem>, width: Float, height: Float) {
        for (item in items) {
            if (item.points.isEmpty()) continue
            when (item.type) {
                AnnotationType.PEN -> drawStroke(canvas, item, width, height, highlighter = false)
                AnnotationType.HIGHLIGHTER -> drawStroke(canvas, item, width, height, highlighter = true)
                AnnotationType.TEXT -> drawNote(canvas, item, width, height)
            }
        }
    }

    private fun drawStroke(canvas: Canvas, item: DrawItem, width: Float, height: Float, highlighter: Boolean) {
        val color = item.colorArgb.toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (highlighter) (color and 0x00FFFFFF) or (HIGHLIGHT_ALPHA shl 24) else color or (0xFF shl 24)
            strokeWidth = max(1f, item.size * width)
            strokeCap = if (highlighter) Paint.Cap.SQUARE else Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val pts = item.points
        if (pts.size == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(pts[0].x * width, pts[0].y * height, paint.strokeWidth / 2f, paint)
            return
        }
        paint.style = Paint.Style.STROKE
        val path = Path()
        path.moveTo(pts[0].x * width, pts[0].y * height)
        for (i in 1 until pts.size) {
            // Smooth the stroke through the midpoints between samples.
            val prev = pts[i - 1]
            val cur = pts[i]
            path.quadTo(prev.x * width, prev.y * height, (prev.x + cur.x) / 2f * width, (prev.y + cur.y) / 2f * height)
        }
        path.lineTo(pts.last().x * width, pts.last().y * height)
        canvas.drawPath(path, paint)
    }

    private fun drawNote(canvas: Canvas, item: DrawItem, width: Float, height: Float) {
        val text = item.note?.takeIf { it.isNotBlank() } ?: return
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = max(8f, item.size * width)
        }
        val padding = textPaint.textSize * 0.35f
        val maxTextWidth = max(40f, 0.4f * width - 2 * padding)
        val desired = Layout.getDesiredWidth(text, textPaint)
        val layoutWidth = min(maxTextWidth, desired).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        val boxW = layout.width + 2 * padding
        val boxH = layout.height + 2 * padding
        val anchor = item.points.first()
        // Keep the note fully inside the page.
        val left = (anchor.x * width).coerceIn(0f, max(0f, width - boxW))
        val top = (anchor.y * height).coerceIn(0f, max(0f, height - boxH))
        val box = RectF(left, top, left + boxW, top + boxH)
        val color = item.colorArgb.toInt()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = (color and 0x00FFFFFF) or (0x99 shl 24); style = Paint.Style.FILL }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color or (0xFF shl 24); style = Paint.Style.STROKE; strokeWidth = max(1f, textPaint.textSize * 0.06f)
        }
        canvas.drawRoundRect(box, padding, padding, fill)
        canvas.drawRoundRect(box, padding, padding, border)
        canvas.save()
        canvas.translate(left + padding, top + padding)
        layout.draw(canvas)
        canvas.restore()
    }
}
