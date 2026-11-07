package com.kosh.android.util

import com.kosh.android.data.db.AnnotationType
import com.kosh.android.data.model.NormalizedPoint
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Compact text form of normalised points: "x,y;x,y;..." with 4 decimals. */
object AnnotationCodec {
    fun encode(points: List<NormalizedPoint>): String =
        points.joinToString(";") { String.format(Locale.US, "%.4f,%.4f", clamp01(it.x), clamp01(it.y)) }

    /** Malformed pairs are skipped and every value is clamped to 0..1, so bad data can't break rendering. */
    fun decode(text: String): List<NormalizedPoint> =
        text.split(';').mapNotNull { pair ->
            val parts = pair.split(',')
            val x = parts.getOrNull(0)?.toFloatOrNull()
            val y = parts.getOrNull(1)?.toFloatOrNull()
            if (parts.size == 2 && x != null && y != null && x.isFinite() && y.isFinite()) NormalizedPoint(clamp01(x), clamp01(y)) else null
        }
}

/** Converts a touch position inside a page of size [width] x [height] pixels to normalised page space. */
fun normalizePoint(x: Float, y: Float, width: Float, height: Float): NormalizedPoint {
    if (width <= 0f || height <= 0f) return NormalizedPoint(0f, 0f)
    return NormalizedPoint(clamp01(x / width), clamp01(y / height))
}

/** Drops points closer than [minDistance] (in normalised units) to keep stored strokes small. Keeps first and last. */
fun thinPoints(points: List<NormalizedPoint>, minDistance: Float = 0.0015f): List<NormalizedPoint> {
    if (points.size <= 2) return points
    val out = ArrayList<NormalizedPoint>(points.size)
    out += points.first()
    for (i in 1 until points.size - 1) {
        val last = out.last()
        if (hypot((points[i].x - last.x).toDouble(), (points[i].y - last.y).toDouble()) >= minDistance) out += points[i]
    }
    out += points.last()
    return out
}

fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0f) return hypot(px - ax, py - ay)
    val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

/** What the screen and the exporter draw: an annotation with its points already decoded. */
data class DrawItem(
    val id: String,
    val type: AnnotationType,
    val colorArgb: Long,
    /** Stroke width as a fraction of page width; for TEXT, the text size as a fraction of page width. */
    val size: Float,
    val points: List<NormalizedPoint>,
    val note: String?,
)

/**
 * Finds the topmost annotation under a tap (pixel coordinates inside a page of [width] x [height]).
 * [tolerancePx] is the extra touch slack around the drawn shape.
 */
fun hitTest(items: List<DrawItem>, x: Float, y: Float, width: Float, height: Float, tolerancePx: Float): DrawItem? {
    for (item in items.asReversed()) {
        if (item.points.isEmpty()) continue
        val pts = item.points.map { it.x * width to it.y * height }
        val reach = tolerancePx + item.size * width / 2f
        val hit = when (item.type) {
            AnnotationType.TEXT -> {
                // A text note starts at its anchor and extends right and down; use a generous box.
                val (ax, ay) = pts.first()
                val boxW = max(0.35f * width, 1f)
                val boxH = max(item.size * width * 4f, 1f)
                x >= ax - tolerancePx && x <= ax + boxW + tolerancePx && y >= ay - tolerancePx && y <= ay + boxH + tolerancePx
            }
            else -> {
                if (pts.size == 1) hypot(x - pts[0].first, y - pts[0].second) <= reach
                else (0 until pts.size - 1).any { i ->
                    distanceToSegment(x, y, pts[i].first, pts[i].second, pts[i + 1].first, pts[i + 1].second) <= reach
                }
            }
        }
        if (hit) return item
    }
    return null
}

/** Bounds check helpers for page navigation from annotation/bookmark lists. */
fun pagesWithAnnotations(pageIndices: List<Int>): List<Int> = pageIndices.distinct().sorted()

