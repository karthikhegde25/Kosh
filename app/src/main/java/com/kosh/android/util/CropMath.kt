package com.kosh.android.util

import com.kosh.android.data.model.CropCorners
import com.kosh.android.data.model.NormalizedPoint
import kotlin.math.hypot
import kotlin.math.max

/** Smallest allowed gap between opposite corners (fraction of the image), so the quad can't collapse. */
const val MIN_CORNER_GAP = 0.08f

fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

fun clampPoint(p: NormalizedPoint) = NormalizedPoint(clamp01(p.x), clamp01(p.y))

fun defaultCorners(inset: Float = 0.04f) = CropCorners(
    topLeft = NormalizedPoint(inset, inset),
    topRight = NormalizedPoint(1f - inset, inset),
    bottomRight = NormalizedPoint(1f - inset, 1f - inset),
    bottomLeft = NormalizedPoint(inset, 1f - inset),
)

fun fullImageCorners() = defaultCorners(0f)

fun CropCorners.isFullImage(): Boolean = this == fullImageCorners()

/** True when every corner is inside the image and the corners keep their natural order (no crossing). */
fun isValidCorners(c: CropCorners, gap: Float = MIN_CORNER_GAP): Boolean {
    val all = listOf(c.topLeft, c.topRight, c.bottomRight, c.bottomLeft)
    if (all.any { it.x !in 0f..1f || it.y !in 0f..1f }) return false
    return c.topLeft.x + gap <= c.topRight.x &&
        c.bottomLeft.x + gap <= c.bottomRight.x &&
        c.topLeft.y + gap <= c.bottomLeft.y &&
        c.topRight.y + gap <= c.bottomRight.y
}

/** The corners as [x0,y0, x1,y1, ...] in pixels, clockwise from the top-left. */
fun CropCorners.toPixels(widthPx: Int, heightPx: Int): FloatArray = floatArrayOf(
    topLeft.x * widthPx, topLeft.y * heightPx,
    topRight.x * widthPx, topRight.y * heightPx,
    bottomRight.x * widthPx, bottomRight.y * heightPx,
    bottomLeft.x * widthPx, bottomLeft.y * heightPx,
)

/** Size of the flattened page: the longer of each pair of opposite edges, so no content is squeezed. */
fun outputSize(c: CropCorners, widthPx: Int, heightPx: Int): Pair<Int, Int> {
    val p = c.toPixels(widthPx, heightPx)
    fun dist(a: Int, b: Int) = hypot((p[a * 2] - p[b * 2]).toDouble(), (p[a * 2 + 1] - p[b * 2 + 1]).toDouble())
    val w = max(dist(0, 1), dist(3, 2))
    val h = max(dist(0, 3), dist(1, 2))
    return max(1, w.toInt()) to max(1, h.toInt())
}

/** Normalises a rotation to 0, 90, 180 or 270. */
fun normalizeRotation(degrees: Int): Int = ((degrees % 360) + 360) % 360

/** Moves the item at [index] by [delta] positions, staying inside the list. Returns the list unchanged if impossible. */
fun <T> moveItem(list: List<T>, index: Int, delta: Int): List<T> {
    val target = index + delta
    if (index !in list.indices || target !in list.indices || delta == 0) return list
    val mutable = list.toMutableList()
    val item = mutable.removeAt(index)
    mutable.add(target, item)
    return mutable
}
