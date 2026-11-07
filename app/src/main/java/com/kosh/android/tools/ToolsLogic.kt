package com.kosh.android.tools

import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/** Everything in the Tools tab. [IMAGE_TO_PDF] is the existing scanner; the rest work on PDFs the user picks. */
enum class PdfTool(val id: String) {
    IMAGE_TO_PDF("image_to_pdf"),
    PDF_TO_IMAGES("pdf_to_images"),
    DOCX_TO_PDF("docx_to_pdf"),
    PAGES_PER_SHEET("pages_per_sheet"),
    MERGE("merge"),
    SPLIT("split"),
    EXTRACT("extract"),
    ROTATE("rotate"),
    DELETE("delete"),
    REORDER("reorder"),
    PAGE_NUMBERS("page_numbers"),
    COMPRESS("compress"),
    ENCRYPT("encrypt"),
    DECRYPT("decrypt"),
    ;

    companion object {
        fun fromId(id: String?): PdfTool? = entries.firstOrNull { it.id == id }
    }
}

enum class SplitMode { RANGES, EVERY_PAGE, EVERY_N }

enum class ImageFormat(val extension: String, val mime: String) { JPG("jpg", "image/jpeg"), PNG("png", "image/png") }

/** Encryption strength for protected PDFs. Both use AES. */
enum class EncryptStrength(val bits: Int) { STANDARD(128), STRONG(256) }

enum class PageNumberFormat { NUMBER, PAGE_N, N_OF_TOTAL, PAGE_N_OF_TOTAL }

enum class NumberPosition(val bottom: Boolean, val align: Int) {
    BOTTOM_LEFT(true, -1), BOTTOM_CENTER(true, 0), BOTTOM_RIGHT(true, 1),
    TOP_LEFT(false, -1), TOP_CENTER(false, 0), TOP_RIGHT(false, 1),
}

enum class NumberSize(val points: Float) { SMALL(9f), MEDIUM(12f), LARGE(16f) }

data class NumberingStyle(
    val format: PageNumberFormat,
    val position: NumberPosition,
    val startAt: Int,
    val size: NumberSize,
    val skipFirst: Boolean,
)

// ---- several pages on one sheet (Word to PDF) ----------------------------------------------------------------------

/** How many pages can share one sheet. */
val SHEET_PAGE_COUNTS = listOf(1, 2, 4, 6, 9, 16)

/** Sheets are A4, in points. Landscape swaps the two. */
const val SHEET_A4_WIDTH = 595.28f
const val SHEET_A4_HEIGHT = 841.89f

const val BORDER_MIN = 0.5f
const val BORDER_MAX = 8f

/** Layout of the sheets: pages per sheet, which way the sheet is turned, and an optional frame around every page. */
data class NUpStyle(
    val perSheet: Int = 1,
    val landscape: Boolean = false,
    val border: Boolean = false,
    /** Line width in points, used only when [border] is on. */
    val borderWidth: Float = 1f,
)

/** Columns and rows for [perSheet] pages on a sheet that is portrait or landscape. */
fun nUpGrid(perSheet: Int, landscape: Boolean): Pair<Int, Int> {
    val n = perSheet.coerceAtLeast(1)
    // Columns and rows for a portrait sheet.
    val portrait = when (n) {
        1 -> 1 to 1
        2 -> 1 to 2
        4 -> 2 to 2
        6 -> 2 to 3
        9 -> 3 to 3
        16 -> 4 to 4
        else -> {
            val columns = ceil(sqrt(n.toFloat())).toInt()
            columns to ceil(n / columns.toFloat()).toInt()
        }
    }
    return if (landscape) portrait.second to portrait.first else portrait
}

/** Space left around the whole sheet and between the pages on it. Nothing at all when one page fills the sheet. */
fun sheetMargin(perSheet: Int): Float = if (perSheet <= 1) 0f else 18f
fun sheetGap(perSheet: Int): Float = when {
    perSheet <= 1 -> 0f
    perSheet >= 9 -> 6f
    else -> 10f
}

fun sheetCount(pages: Int, perSheet: Int): Int = if (pages <= 0 || perSheet <= 0) 0 else (pages + perSheet - 1) / perSheet

/** A rectangle in PDF space (origin bottom-left). */
data class SheetCell(val x: Float, val y: Float, val width: Float, val height: Float)

/** The slots on a sheet, in reading order: left to right, then top to bottom. */
fun nUpCells(sheetWidth: Float, sheetHeight: Float, columns: Int, rows: Int, margin: Float, gap: Float): List<SheetCell> {
    val w = (sheetWidth - 2 * margin - (columns - 1) * gap) / columns
    val h = (sheetHeight - 2 * margin - (rows - 1) * gap) / rows
    return List(columns * rows) { i ->
        val col = i % columns
        val row = i / columns
        SheetCell(margin + col * (w + gap), sheetHeight - margin - (row + 1) * h - row * gap, w, h)
    }
}

/** Where a page of [srcWidth] x [srcHeight] lands inside [cell]: as large as fits, never stretched, centred. */
data class Placement(val x: Float, val y: Float, val width: Float, val height: Float, val scale: Float)

fun fitInto(srcWidth: Float, srcHeight: Float, cell: SheetCell): Placement {
    val scale = min(cell.width / srcWidth, cell.height / srcHeight)
    val w = srcWidth * scale
    val h = srcHeight * scale
    return Placement(cell.x + (cell.width - w) / 2f, cell.y + (cell.height - h) / 2f, w, h, scale)
}

// ---- page ranges ------------------------------------------------------------------------------------

/** One part like "5", "2-4", "7-" (to the end) or "-3" (from the start), as 0-based page indexes. Null if invalid. */
fun parseRangePart(part: String, total: Int): List<Int>? {
    val p = part.filterNot { it.isWhitespace() }
    if (p.isEmpty() || total <= 0) return null
    val dash = p.indexOf('-')
    if (dash < 0) {
        val n = p.toIntOrNull() ?: return null
        return if (n in 1..total) listOf(n - 1) else null
    }
    val a = p.substring(0, dash)
    val b = p.substring(dash + 1)
    val start = if (a.isEmpty()) 1 else a.toIntOrNull() ?: return null
    val end = if (b.isEmpty()) total else b.toIntOrNull() ?: return null
    if (start !in 1..total || end !in 1..total || start > end) return null
    return (start - 1 until end).toList()
}

/** "1-3, 5, 8-" -> sorted, unique 0-based pages. Null if empty or any part is invalid. */
fun parsePageRanges(text: String, total: Int): List<Int>? {
    val parts = text.split(',', ';').filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    val out = sortedSetOf<Int>()
    for (part in parts) out += parseRangePart(part, total) ?: return null
    return out.toList()
}

/** Each comma-separated part becomes its own group (used by Split). Null if empty or any part is invalid. */
fun parseRangeGroups(text: String, total: Int): List<List<Int>>? {
    val parts = text.split(',', ';').filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    return parts.map { parseRangePart(it, total) ?: return null }
}

/** Pages are shown 1-based, so the 0-based set [0, 1, 2, 4] becomes "1-3, 5". */
fun formatPageRanges(pages: Collection<Int>): String {
    val sorted = pages.toSortedSet().toList()
    if (sorted.isEmpty()) return ""
    val parts = ArrayList<String>()
    var start = sorted[0]
    var prev = start
    fun flush() { parts += if (start == prev) "${start + 1}" else "${start + 1}-${prev + 1}" }
    for (i in 1 until sorted.size) {
        if (sorted[i] == prev + 1) prev = sorted[i] else { flush(); start = sorted[i]; prev = start }
    }
    flush()
    return parts.joinToString(", ")
}

fun splitEveryN(total: Int, n: Int): List<List<Int>> =
    if (total <= 0 || n <= 0) emptyList() else (0 until total).chunked(n)

// ---- page numbers -------------------------------------------------------------------------------------

/** Text for the page at [outputIndex] (0-based) of [count] output pages, or null when that page is left unnumbered. */
fun pageLabel(outputIndex: Int, count: Int, style: NumberingStyle): String? {
    if (style.skipFirst && outputIndex == 0) return null
    val numbered = if (style.skipFirst) count - 1 else count
    val shown = style.startAt + outputIndex - if (style.skipFirst) 1 else 0
    val last = style.startAt + numbered - 1
    return when (style.format) {
        PageNumberFormat.NUMBER -> "$shown"
        PageNumberFormat.PAGE_N -> "Page $shown"
        PageNumberFormat.N_OF_TOTAL -> "$shown / $last"
        PageNumberFormat.PAGE_N_OF_TOTAL -> "Page $shown of $last"
    }
}

/** Where to draw the label: left edge of the text and its baseline, in the same units as the page. */
fun numberOrigin(position: NumberPosition, pageWidth: Float, pageHeight: Float, margin: Float, textWidth: Float, textSize: Float): Pair<Float, Float> {
    val x = when (position.align) {
        -1 -> margin
        1 -> pageWidth - margin - textWidth
        else -> (pageWidth - textWidth) / 2f
    }
    val y = if (position.bottom) pageHeight - margin else margin + textSize * 0.8f
    return x to y
}

/** Rounds a page rotation to 0, 90, 180 or 270. */
fun quarterTurns(rotation: Int): Int = ((rotation % 360) + 360) % 360 / 90 * 90

/**
 * Converts a point on the page as it is displayed (origin bottom-left of the turned page) into PDF user space for a page
 * whose box is [boxWidth] x [boxHeight] and which is displayed turned by [rotation] degrees clockwise.
 */
fun userSpacePoint(rotation: Int, boxWidth: Float, boxHeight: Float, displayX: Float, displayY: Float): Pair<Float, Float> =
    when (quarterTurns(rotation)) {
        90 -> (boxWidth - displayY) to displayX
        180 -> (boxWidth - displayX) to (boxHeight - displayY)
        270 -> displayY to (boxHeight - displayX)
        else -> displayX to displayY
    }

/** Where to start the page number in PDF user space, and how much to turn the text so it reads upright when displayed. */
data class NumberPlacement(val x: Float, val y: Float, val angleDegrees: Float)

fun numberPlacement(position: NumberPosition, rotation: Int, boxWidth: Float, boxHeight: Float, margin: Float, textWidth: Float, textSize: Float): NumberPlacement {
    val r = quarterTurns(rotation)
    val swap = r == 90 || r == 270
    val shownW = if (swap) boxHeight else boxWidth
    val shownH = if (swap) boxWidth else boxHeight
    val (x, yFromTop) = numberOrigin(position, shownW, shownH, margin, textWidth, textSize)
    val (ux, uy) = userSpacePoint(r, boxWidth, boxHeight, x, shownH - yFromTop)
    return NumberPlacement(ux, uy, r.toFloat())
}

// ---- sizes & names --------------------------------------------------------------------------------------

/** Pixel size for rendering a page of the given size in points at [dpi], never wider than [maxWidthPx]. */
fun renderWidthPx(widthPts: Int, dpi: Int, maxWidthPx: Int = 2560): Int =
    (widthPts * dpi / 72f).toInt().coerceIn(1, maxWidthPx)

/** Page size in points after turning the page by [rotation] degrees (multiples of 90). */
fun rotatedSize(widthPts: Int, heightPts: Int, rotation: Int): Pair<Int, Int> =
    if (((rotation % 360) + 360) % 360 % 180 == 90) heightPts to widthPts else widthPts to heightPts

private val FORBIDDEN = Regex("""[\\/:*?"<>|]""")

private fun cleanBase(base: String): String =
    base.removeSuffix(".pdf").removeSuffix(".PDF").replace(FORBIDDEN, "_").trim().trim('.').ifEmpty { "Document" }

/** "Report" + "rotated" -> "Report (rotated).pdf". */
fun toolFileName(base: String, suffix: String, extension: String = "pdf"): String = "${cleanBase(base)} ($suffix).$extension"

/** "Report.docx" -> "Report.pdf". */
fun docxPdfFileName(base: String): String =
    "${cleanBase(base.replace(Regex("""\.docx$""", RegexOption.IGNORE_CASE), ""))}.pdf"

/** "Report", page 3 of 120 -> "Report_page_003.jpg". */
fun imageFileName(base: String, page: Int, total: Int, extension: String): String {
    val width = total.toString().length.coerceAtLeast(2)
    return "${cleanBase(base)}_page_${page.toString().padStart(width, '0')}.$extension"
}

/** "Report", part 2 of 12 -> "Report (part 02).pdf". */
fun partFileName(base: String, part: Int, total: Int): String {
    val width = total.toString().length.coerceAtLeast(1)
    return "${cleanBase(base)} (part ${part.toString().padStart(width, '0')}).pdf"
}
