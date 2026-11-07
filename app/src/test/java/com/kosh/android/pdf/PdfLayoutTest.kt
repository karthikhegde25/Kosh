package com.kosh.android.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLayoutTest {

    private fun onPages(
        paper: PaperSize = PaperSize.A4,
        orientation: PageOrientation = PageOrientation.AUTO,
    ) = PdfExportOptions(layout = LayoutMode.ON_PAGES, paper = paper, orientation = orientation)

    @Test
    fun imagesAsPagesMakePagesTheShapeOfTheImage() {
        val landscape = computeGeometry(4000, 3000, PdfExportOptions(layout = LayoutMode.AS_PAGES))
        assertEquals(842f, landscape.pageWidth, 0.01f)
        assertEquals(631.5f, landscape.pageHeight, 0.01f)
        val portrait = computeGeometry(3000, 4000, PdfExportOptions(layout = LayoutMode.AS_PAGES))
        assertEquals(842f, portrait.pageHeight, 0.01f)
        assertEquals(631.5f, portrait.pageWidth, 0.01f)
    }

    @Test
    fun imagesAsPagesFillTheWholePage() {
        val g = computeGeometry(1000, 2000, PdfExportOptions(layout = LayoutMode.AS_PAGES))
        assertEquals(0f, g.left, 0f)
        assertEquals(0f, g.top, 0f)
        assertEquals(g.pageWidth, g.right, 0.01f)
        assertEquals(g.pageHeight, g.bottom, 0.01f)
    }

    @Test
    fun imagesOnPagesUseAWhiteA4PageWithMargins() {
        val g = computeGeometry(3000, 4000, onPages())
        assertEquals(595f, g.pageWidth, 0.01f)
        assertEquals(842f, g.pageHeight, 0.01f)
        // Width-limited: fills the width between the 36pt margins and is centred vertically.
        assertEquals(36f, g.left, 0.01f)
        assertEquals(595f - 36f, g.right, 0.01f)
        assertEquals((842f - g.imageHeight) / 2f, g.top, 0.01f)
    }

    @Test
    fun imagesOnPagesNeverStretch() {
        for ((w, h) in listOf(3000 to 4000, 4000 to 3000, 1000 to 1000, 500 to 3000)) {
            val g = computeGeometry(w, h, onPages())
            assertEquals(w.toFloat() / h, g.imageWidth / g.imageHeight, 0.001f)
        }
    }

    @Test
    fun imagesStayInsideTheMargins() {
        for ((w, h) in listOf(3000 to 4000, 4000 to 3000, 100 to 100, 800 to 5000)) {
            val g = computeGeometry(w, h, onPages(PaperSize.LETTER))
            assertTrue(g.left >= PAGE_MARGIN_PTS - 0.01f)
            assertTrue(g.top >= PAGE_MARGIN_PTS - 0.01f)
            assertTrue(g.right <= g.pageWidth - PAGE_MARGIN_PTS + 0.01f)
            assertTrue(g.bottom <= g.pageHeight - PAGE_MARGIN_PTS + 0.01f)
        }
    }

    @Test
    fun orientationAutoFollowsTheImage() {
        val land = computeGeometry(4000, 3000, onPages())
        assertEquals(842f, land.pageWidth, 0.01f)
        assertEquals(595f, land.pageHeight, 0.01f)
    }

    @Test
    fun forcedOrientationOverridesTheImage() {
        val portrait = computeGeometry(4000, 3000, onPages(orientation = PageOrientation.PORTRAIT))
        assertEquals(595f, portrait.pageWidth, 0.01f)
        val landscape = computeGeometry(3000, 4000, onPages(orientation = PageOrientation.LANDSCAPE))
        assertEquals(842f, landscape.pageWidth, 0.01f)
    }

    @Test
    fun letterPaperUsesLetterDimensions() {
        val g = computeGeometry(3000, 4000, onPages(PaperSize.LETTER))
        assertEquals(612f, g.pageWidth, 0.01f)
        assertEquals(792f, g.pageHeight, 0.01f)
    }
}
