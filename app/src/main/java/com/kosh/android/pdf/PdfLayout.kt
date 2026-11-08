package com.kosh.android.pdf

import kotlin.math.min

/** How scanned images become PDF pages. */
enum class LayoutMode {
    /** Every image is its own page, sized to the image's shape. */
    AS_PAGES,

    /** Every image is placed on a white paper-size page (A4 / Letter), centred with margins. */
    ON_PAGES,
}

enum class PaperSize(val widthPts: Float, val heightPts: Float) {
    A4(595f, 842f),
    LETTER(612f, 792f),
}

enum class PageOrientation { AUTO, PORTRAIT, LANDSCAPE }

enum class ExportQuality(val maxDimPx: Int, val jpegQuality: Int) {
    STANDARD(1800, 80),
    HIGH(2800, 92),
}

data class PdfExportOptions(
    val layout: LayoutMode = LayoutMode.AS_PAGES,
    val paper: PaperSize = PaperSize.A4,
    val orientation: PageOrientation = PageOrientation.AUTO,
    val quality: ExportQuality = ExportQuality.STANDARD,
)

/** Page size and the rectangle the image occupies, in PDF points with the origin at the top-left. */
data class PageGeometry(
    val pageWidth: Float,
    val pageHeight: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val imageWidth get() = right - left
    val imageHeight get() = bottom - top
}

const val PAGE_MARGIN_PTS = 36f // half an inch
private const val AS_PAGES_LONG_SIDE = 842f

/** Never stretches: the image keeps its aspect ratio in both modes. */
fun computeGeometry(imageWidthPx: Int, imageHeightPx: Int, options: PdfExportOptions): PageGeometry {
    require(imageWidthPx > 0 && imageHeightPx > 0) { "image must have a size" }
    val w = imageWidthPx.toFloat()
    val h = imageHeightPx.toFloat()
    return when (options.layout) {
        LayoutMode.AS_PAGES -> {
            val pageW: Float
            val pageH: Float
            if (w >= h) {
                pageW = AS_PAGES_LONG_SIDE
                pageH = AS_PAGES_LONG_SIDE * h / w
            } else {
                pageH = AS_PAGES_LONG_SIDE
                pageW = AS_PAGES_LONG_SIDE * w / h
            }
            PageGeometry(pageW, pageH, 0f, 0f, pageW, pageH)
        }
        LayoutMode.ON_PAGES -> {
            val landscape = when (options.orientation) {
                PageOrientation.AUTO -> w > h
                PageOrientation.PORTRAIT -> false
                PageOrientation.LANDSCAPE -> true
            }
            val pageW = if (landscape) options.paper.heightPts else options.paper.widthPts
            val pageH = if (landscape) options.paper.widthPts else options.paper.heightPts
            val areaW = pageW - 2 * PAGE_MARGIN_PTS
            val areaH = pageH - 2 * PAGE_MARGIN_PTS
            val scale = min(areaW / w, areaH / h)
            val drawW = w * scale
            val drawH = h * scale
            val left = (pageW - drawW) / 2f
            val top = (pageH - drawH) / 2f
            PageGeometry(pageW, pageH, left, top, left + drawW, top + drawH)
        }
    }
}
