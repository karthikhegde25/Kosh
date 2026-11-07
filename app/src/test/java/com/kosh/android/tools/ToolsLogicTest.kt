package com.kosh.android.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsLogicTest {

    @Test fun rangesParseToZeroBasedPages() {
        assertEquals(listOf(0, 1, 2, 4), parsePageRanges("1-3, 5", 10))
        assertEquals(listOf(7, 8, 9), parsePageRanges("8-", 10))
        assertEquals(listOf(0, 1), parsePageRanges("-2", 10))
        assertEquals(listOf(0, 2), parsePageRanges("3, 1, 3", 10))
        assertEquals(listOf(1, 2, 3), parsePageRanges(" 2 - 4 ", 10))
        assertEquals(listOf(0), parsePageRanges("1,", 10))
    }

    @Test fun badRangesAreRejected() {
        assertNull(parsePageRanges("", 10))
        assertNull(parsePageRanges("0", 10))
        assertNull(parsePageRanges("11", 10))
        assertNull(parsePageRanges("5-3", 10))
        assertNull(parsePageRanges("1-2-3", 10))
        assertNull(parsePageRanges("a", 10))
        assertNull(parsePageRanges("1, x", 10))
        assertNull(parsePageRanges("1", 0))
    }

    @Test fun rangeGroupsStaySeparate() {
        assertEquals(listOf(listOf(0, 1, 2), listOf(4), listOf(5, 6)), parseRangeGroups("1-3, 5, 6-7", 10))
        assertNull(parseRangeGroups("1-3, 12", 10))
    }

    @Test fun pagesFormatBackIntoRanges() {
        assertEquals("1-3, 5", formatPageRanges(listOf(0, 1, 2, 4)))
        assertEquals("2", formatPageRanges(setOf(1)))
        assertEquals("1, 3, 5", formatPageRanges(listOf(4, 0, 2)))
        assertEquals("", formatPageRanges(emptyList<Int>()))
        val pages = listOf(0, 3, 4, 5, 9)
        assertEquals(pages, parsePageRanges(formatPageRanges(pages), 10))
    }

    @Test fun splitEveryN() {
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), splitEveryN(5, 2))
        assertEquals(5, splitEveryN(5, 1).size)
        assertTrue(splitEveryN(5, 0).isEmpty())
        assertTrue(splitEveryN(0, 3).isEmpty())
    }

    private fun style(format: PageNumberFormat, startAt: Int = 1, skipFirst: Boolean = false) =
        NumberingStyle(format, NumberPosition.BOTTOM_CENTER, startAt, NumberSize.MEDIUM, skipFirst)

    @Test fun pageLabels() {
        assertEquals("1", pageLabel(0, 5, style(PageNumberFormat.NUMBER)))
        assertEquals("Page 3", pageLabel(2, 5, style(PageNumberFormat.PAGE_N)))
        assertEquals("5 / 5", pageLabel(4, 5, style(PageNumberFormat.N_OF_TOTAL)))
        assertEquals("Page 1 of 5", pageLabel(0, 5, style(PageNumberFormat.PAGE_N_OF_TOTAL)))
        assertEquals("10", pageLabel(0, 3, style(PageNumberFormat.NUMBER, startAt = 10)))
        assertEquals("12 / 12", pageLabel(2, 3, style(PageNumberFormat.N_OF_TOTAL, startAt = 10)))
    }

    @Test fun skippingTheFirstPageStartsNumberingOnPageTwo() {
        val s = style(PageNumberFormat.N_OF_TOTAL, skipFirst = true)
        assertNull(pageLabel(0, 4, s))
        assertEquals("1 / 3", pageLabel(1, 4, s))
        assertEquals("3 / 3", pageLabel(3, 4, s))
    }

    @Test fun numberOrigins() {
        val (lx, ly) = numberOrigin(NumberPosition.BOTTOM_LEFT, 600f, 800f, 30f, 40f, 12f)
        assertEquals(30f, lx, 0f); assertEquals(770f, ly, 0f)
        val (cx, _) = numberOrigin(NumberPosition.TOP_CENTER, 600f, 800f, 30f, 40f, 12f)
        assertEquals(280f, cx, 0f)
        val (rx, ry) = numberOrigin(NumberPosition.TOP_RIGHT, 600f, 800f, 30f, 40f, 10f)
        assertEquals(530f, rx, 0f); assertEquals(38f, ry, 0.001f)
    }

    @Test fun sizesAndRotation() {
        assertEquals(1239, renderWidthPx(595, 150))
        assertEquals(2560, renderWidthPx(2000, 300))
        assertEquals(842 to 595, rotatedSize(595, 842, 90))
        assertEquals(595 to 842, rotatedSize(595, 842, 180))
        assertEquals(842 to 595, rotatedSize(595, 842, -90))
        assertEquals(595 to 842, rotatedSize(595, 842, 360))
    }

    @Test fun fileNames() {
        assertEquals("Report (rotated).pdf", toolFileName("Report.pdf", "rotated"))
        assertEquals("a_b (merged).pdf", toolFileName("a/b", "merged"))
        assertEquals("Document (merged).pdf", toolFileName("  ", "merged"))
        assertEquals("Report_page_003.jpg", imageFileName("Report", 3, 120, "jpg"))
        assertEquals("Report_page_07.png", imageFileName("Report", 7, 9, "png"))
        assertEquals("Report (part 02).pdf", partFileName("Report", 2, 12))
        assertEquals("Report (part 2).pdf", partFileName("Report", 2, 5))
    }

    @Test fun toolIdsRoundTrip() {
        PdfTool.entries.forEach { assertEquals(it, PdfTool.fromId(it.id)) }
        assertNull(PdfTool.fromId("nope"))
    }

    private fun place(position: NumberPosition, rotation: Int) =
        numberPlacement(position, rotation, 595f, 842f, 28f, 90f, 12f)

    @Test fun quarterTurnsSnapToRightAngles() {
        assertEquals(0, quarterTurns(0)); assertEquals(90, quarterTurns(90)); assertEquals(270, quarterTurns(-90))
        assertEquals(0, quarterTurns(360)); assertEquals(180, quarterTurns(540))
    }

    // These values were checked by drawing the numbers into real rotated PDFs and comparing the rendered pages.
    @Test fun pageNumbersLandInTheRightCornerOnTurnedPages() {
        fun check(p: NumberPlacement, x: Float, y: Float, angle: Float) {
            assertEquals(x, p.x, 0.01f); assertEquals(y, p.y, 0.01f); assertEquals(angle, p.angleDegrees, 0f)
        }
        check(place(NumberPosition.BOTTOM_LEFT, 0), 28f, 28f, 0f)
        check(place(NumberPosition.BOTTOM_CENTER, 0), 252.5f, 28f, 0f)
        check(place(NumberPosition.TOP_RIGHT, 0), 477f, 804.4f, 0f)
        check(place(NumberPosition.BOTTOM_LEFT, 90), 567f, 28f, 90f)
        check(place(NumberPosition.TOP_LEFT, 90), 37.6f, 28f, 90f)
        check(place(NumberPosition.BOTTOM_RIGHT, 90), 567f, 724f, 90f)
        check(place(NumberPosition.BOTTOM_LEFT, 180), 567f, 814f, 180f)
        check(place(NumberPosition.TOP_RIGHT, 180), 118f, 37.6f, 180f)
        check(place(NumberPosition.BOTTOM_LEFT, 270), 28f, 814f, 270f)
        check(place(NumberPosition.TOP_LEFT, 270), 557.4f, 814f, 270f)
        check(place(NumberPosition.BOTTOM_CENTER, -90), 28f, 466f, 270f)
    }

    @Test fun userSpacePointsFollowThePageTurn() {
        assertEquals(10f to 20f, userSpacePoint(0, 100f, 200f, 10f, 20f))
        assertEquals(80f to 10f, userSpacePoint(90, 100f, 200f, 10f, 20f))
        assertEquals(90f to 180f, userSpacePoint(180, 100f, 200f, 10f, 20f))
        assertEquals(20f to 190f, userSpacePoint(270, 100f, 200f, 10f, 20f))
    }
    // ---- several pages on one sheet ----

    @Test fun gridsFollowTheSheetOrientation() {
        assertEquals(1 to 1, nUpGrid(1, false))
        assertEquals(1 to 2, nUpGrid(2, false))
        assertEquals(2 to 1, nUpGrid(2, true))
        assertEquals(2 to 2, nUpGrid(4, true))
        assertEquals(2 to 3, nUpGrid(6, false))
        assertEquals(3 to 2, nUpGrid(6, true))
        assertEquals(3 to 3, nUpGrid(9, false))
        assertEquals(4 to 4, nUpGrid(16, true))
        for (n in SHEET_PAGE_COUNTS) for (landscape in listOf(false, true)) {
            val (c, r) = nUpGrid(n, landscape)
            assertEquals(n, c * r)
        }
    }

    @Test fun sheetsNeededForThePages() {
        assertEquals(0, sheetCount(0, 4))
        assertEquals(1, sheetCount(1, 4))
        assertEquals(1, sheetCount(4, 4))
        assertEquals(2, sheetCount(5, 4))
        assertEquals(7, sheetCount(7, 1))
    }

    @Test fun cellsFillTheSheetInReadingOrder() {
        val cells = nUpCells(600f, 800f, 2, 2, 20f, 10f)
        assertEquals(4, cells.size)
        assertEquals(275f, cells[0].width, 0.001f)
        assertEquals(375f, cells[0].height, 0.001f)
        // First cell is top-left (largest y), second is to its right, third is below the first.
        assertEquals(20f, cells[0].x, 0.001f)
        assertEquals(405f, cells[0].y, 0.001f) // 800 - 20 - 375
        assertEquals(305f, cells[1].x, 0.001f)
        assertEquals(cells[0].y, cells[1].y, 0.001f)
        assertEquals(20f, cells[2].y, 0.001f)
        assertEquals(cells[0].x, cells[2].x, 0.001f)
        // Nothing sticks out of the margins.
        for (c in cells) {
            assertTrue(c.x >= 20f - 0.001f && c.x + c.width <= 580f + 0.001f)
            assertTrue(c.y >= 20f - 0.001f && c.y + c.height <= 780f + 0.001f)
        }
    }

    @Test fun pagesAreScaledToFitAndCentred() {
        val cell = SheetCell(0f, 0f, 200f, 100f)
        val tall = fitInto(100f, 200f, cell) // limited by the height
        assertEquals(0.5f, tall.scale, 0.001f)
        assertEquals(50f, tall.width, 0.001f)
        assertEquals(75f, tall.x, 0.001f)
        val wide = fitInto(400f, 100f, cell) // limited by the width
        assertEquals(0.5f, wide.scale, 0.001f)
        assertEquals(25f, wide.y, 0.001f)
    }

    @Test fun onePagePerSheetUsesTheWholeSheet() {
        assertEquals(0f, sheetMargin(1), 0f)
        val (c, r) = nUpGrid(1, false)
        val cell = nUpCells(SHEET_A4_WIDTH, SHEET_A4_HEIGHT, c, r, sheetMargin(1), sheetGap(1)).single()
        val placed = fitInto(595f, 842f, cell)
        assertEquals(1f, placed.scale, 0.01f)
    }

    @Test fun wordFilesBecomePdfNames() {
        assertEquals("Report.pdf", docxPdfFileName("Report.docx"))
        assertEquals("Report.pdf", docxPdfFileName("Report.DOCX"))
        assertEquals("a_b.pdf", docxPdfFileName("a:b.docx"))
        assertEquals("Document.pdf", docxPdfFileName(".docx"))
    }
}
