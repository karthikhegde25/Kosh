package com.kosh.android.viewer

import kotlin.math.abs
import kotlin.math.roundToInt

const val MIN_VIEWER_SCALE = 0.1f
const val MAX_VIEWER_SCALE = 10f

/** Tap-to-turn: the outer quarters turn pages, the middle toggles the toolbar. */
fun tapZone(x: Float, width: Float): TapZone = when {
    width <= 0f -> TapZone.CENTER
    x < width * 0.25f -> TapZone.PREVIOUS
    x > width * 0.75f -> TapZone.NEXT
    else -> TapZone.CENTER
}

fun clampScale(scale: Float): Float = scale.coerceIn(MIN_VIEWER_SCALE, MAX_VIEWER_SCALE)

/** Double-tap: go to the preset zoom, or back to fit-width when already there. */
fun doubleTapTargetScale(current: Float, fitWidth: Float, preset: Float): Float =
    clampScale(if (abs(current - preset) > 0.02f) preset else fitWidth)

fun stepPage(current: Int, total: Int, delta: Int): Int =
    if (total <= 0) 1 else (current + delta).coerceIn(1, total)

fun pageToRatio(page: Int, total: Int): Float =
    if (total <= 1) 0f else ((page - 1).toFloat() / (total - 1)).coerceIn(0f, 1f)

fun ratioToPage(ratio: Float, total: Int): Int =
    if (total <= 1) 1 else (ratio.coerceIn(0f, 1f) * (total - 1)).roundToInt() + 1

/** "12" -> 12 when inside 1..total, otherwise null. */
fun parsePageInput(text: String, total: Int): Int? =
    text.trim().toIntOrNull()?.takeIf { it in 1..total }

/** "#RRGGBB" -> opaque ARGB int, or null if malformed. */
fun hexToArgb(hex: String): Int? {
    val h = hex.trim().removePrefix("#")
    if (h.length != 6) return null
    val v = h.toLongOrNull(16) ?: return null
    return (0xFF000000L or v).toInt()
}

fun argbToHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

/** Highlights in reading order: by page, then top-to-bottom, then left-to-right. */
fun sortHighlights(items: List<HighlightItem>): List<HighlightItem> =
    items.sortedWith(
        compareBy<HighlightItem>({ it.page }, { it.quads.firstOrNull()?.y ?: 0f }, { it.quads.firstOrNull()?.x ?: 0f }, { it.id }),
    )

/** Next/previous highlight after the given page position; wraps around. Returns null when the list is empty. */
fun neighbourHighlight(sorted: List<HighlightItem>, currentId: Long?, forward: Boolean): HighlightItem? {
    if (sorted.isEmpty()) return null
    val i = sorted.indexOfFirst { it.id == currentId }
    if (i < 0) return if (forward) sorted.first() else sorted.last()
    val n = sorted.size
    return sorted[(i + if (forward) 1 else n - 1) % n]
}

data class OutlineRow(val node: OutlineNode, val depth: Int)

/** Flattens the outline tree into visible rows; children show only under expanded parents. */
fun flattenOutline(nodes: List<OutlineNode>, expanded: Set<String>, depth: Int = 0): List<OutlineRow> {
    val out = ArrayList<OutlineRow>()
    for (n in nodes) {
        out += OutlineRow(n, depth)
        if (n.children.isNotEmpty() && n.id in expanded) out += flattenOutline(n.children, expanded, depth + 1)
    }
    return out
}

/** Auto-scroll speed presets in CSS px per second. */
val AUTO_SCROLL_PRESETS = listOf(20, 40, 80, 140, 220)
const val MIN_AUTO_SCROLL_PX = 10
const val MAX_AUTO_SCROLL_PX = 400

/** Quick zoom presets offered in the zoom panel. */
val QUICK_ZOOM_STEPS = listOf(0.5f, 0.75f, 1f, 1.5f, 2f, 3f)

/** Colours offered for doodling (opaque ARGB). */
val DOODLE_COLORS: List<Int> = listOf(
    0xFF000000.toInt(), 0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(),
    0xFF43A047.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFFFFFFFF.toInt(),
)

/**
 * Name for the doodled copy: "Report (doodle).pdf", then "Report (doodle 2).pdf" and so on until [exists] says it is free.
 * Characters that are not allowed in file names are replaced.
 */
fun doodleFileName(base: String, exists: (String) -> Boolean): String {
    val clean = base.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "Document" }
    var n = 1
    while (true) {
        val name = if (n == 1) "$clean (doodle).pdf" else "$clean (doodle $n).pdf"
        if (!exists(name)) return name
        n++
    }
}
