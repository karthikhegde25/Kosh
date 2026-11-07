package com.kosh.android.util

import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

const val MIN_ZOOM = 0.25f
const val MAX_ZOOM = 4f
const val DOUBLE_TAP_ZOOM = 2.5f

/** Largest single page bitmap we ever allocate (~32 MB at ARGB_8888). */
const val MAX_RENDER_PIXELS = 8_000_000
const val MAX_RENDER_WIDTH_PX = 2560

fun clampPage(page: Int, pageCount: Int): Int =
    if (pageCount <= 0) 0 else page.coerceIn(0, pageCount - 1)

/** Parses user input for "go to page" (1-based). Returns the 0-based index, or null if invalid. */
fun parsePageInput(text: String, pageCount: Int): Int? {
    val n = text.trim().toIntOrNull() ?: return null
    return if (n in 1..pageCount) n - 1 else null
}

fun clampZoom(zoom: Float): Float = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

/** Zoom (relative to fit-to-width = 1.0) at which a whole page fits in the viewport. Never above 1. */
fun fitPageZoom(viewportWidth: Float, viewportHeight: Float, pageWidth: Float, pageHeight: Float): Float {
    if (viewportWidth <= 0f || pageWidth <= 0f || pageHeight <= 0f) return 1f
    val heightAtFitWidth = viewportWidth * pageHeight / pageWidth
    return clampZoom(min(1f, viewportHeight / heightAtFitWidth))
}

/** Maximum horizontal pan (pixels from centre) for a page zoomed to [zoom]. */
fun maxPanX(viewportWidth: Float, zoom: Float): Float = max(0f, (viewportWidth * zoom - viewportWidth) / 2f)

/**
 * Pixel size for rendering a page: [targetWidth] wide, aspect ratio preserved, but never more than
 * [maxPixels] in total, so a zoomed page can't allocate an enormous bitmap.
 */
fun computeRenderSize(pageWidth: Int, pageHeight: Int, targetWidth: Int, maxPixels: Int = MAX_RENDER_PIXELS): Pair<Int, Int> {
    require(pageWidth > 0 && pageHeight > 0)
    var w = targetWidth.coerceIn(1, MAX_RENDER_WIDTH_PX).toDouble()
    var h = w * pageHeight / pageWidth
    if (w * h > maxPixels) {
        w = sqrt(maxPixels.toDouble() * pageWidth / pageHeight)
        h = w * pageHeight / pageWidth
    }
    return max(1, w.roundToInt()) to max(1, h.roundToInt())
}

/** Rounds a width up to a multiple of [step] so tiny zoom changes don't trigger re-renders. */
fun bucketWidth(widthPx: Float, step: Int = 128): Int =
    (kotlin.math.ceil(widthPx / step).toInt().coerceAtLeast(1)) * step

/** Stable per-document key for saved reading progress. Based on the URI, never on the display name. */
fun documentKey(uri: String): String =
    MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

data class ScrollPosition(val index: Int, val offset: Int)

/**
 * Where to scroll after a zoom so the spot under the pinch stays under the fingers (instead of the zoom
 * always anchoring to the top of the screen).
 *
 * [pageHeights] are the page heights at the NEW zoom level and [gap] is the fixed space between pages.
 * [anchorIndex] is the page that was under the pinch point and [fraction] (0..1) how far down that page the
 * point was. [focalInContent] is the pinch point's distance from the start of the scrolling content.
 */
fun scrollAfterZoom(
    pageHeights: List<Float>,
    gap: Float,
    anchorIndex: Int,
    fraction: Float,
    focalInContent: Float,
): ScrollPosition {
    if (pageHeights.isEmpty()) return ScrollPosition(0, 0)
    val anchor = anchorIndex.coerceIn(0, pageHeights.lastIndex)
    var top = 0f
    for (j in 0 until anchor) top += pageHeights[j] + gap
    // Absolute position of the anchored point in the document, and the scroll that puts it under the fingers.
    val scrollY = (top + fraction.coerceIn(0f, 1f) * pageHeights[anchor] - focalInContent).coerceAtLeast(0f)
    var accumulated = 0f
    var index = 0
    while (index < pageHeights.lastIndex) {
        val step = pageHeights[index] + gap
        if (accumulated + step > scrollY) break
        accumulated += step
        index++
    }
    return ScrollPosition(index, (scrollY - accumulated).roundToInt())
}
