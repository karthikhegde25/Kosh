package com.kosh.android.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerLogicTest {

    private fun hl(id: Long, page: Int, y: Float = 0f, x: Float = 0f) =
        HighlightItem(id, page, "t", listOf(Quad(x, y, 0.1f, 0.01f)), 0, null, null, false)

    @Test fun tapZones() {
        assertEquals(TapZone.PREVIOUS, tapZone(10f, 400f))
        assertEquals(TapZone.NEXT, tapZone(390f, 400f))
        assertEquals(TapZone.CENTER, tapZone(200f, 400f))
        assertEquals(TapZone.CENTER, tapZone(10f, 0f))
    }

    @Test fun doubleTapTogglesBetweenPresetAndFitWidth() {
        assertEquals(2f, doubleTapTargetScale(1f, 1.2f, 2f), 0.0001f)
        assertEquals(1.2f, doubleTapTargetScale(2f, 1.2f, 2f), 0.0001f)
    }

    @Test fun scaleIsClamped() {
        assertEquals(MIN_VIEWER_SCALE, clampScale(0.01f), 0f)
        assertEquals(MAX_VIEWER_SCALE, clampScale(99f), 0f)
        assertEquals(1.5f, clampScale(1.5f), 0f)
    }

    @Test fun pageSteppingStaysInRange() {
        assertEquals(1, stepPage(1, 10, -1))
        assertEquals(10, stepPage(10, 10, 1))
        assertEquals(4, stepPage(3, 10, 1))
        assertEquals(1, stepPage(1, 0, 1))
    }

    @Test fun scrubberRatioRoundTrips() {
        for (p in 1..20) assertEquals(p, ratioToPage(pageToRatio(p, 20), 20))
        assertEquals(1, ratioToPage(0.5f, 1))
        assertEquals(0f, pageToRatio(1, 1), 0f)
    }

    @Test fun pageInputValidation() {
        assertEquals(5, parsePageInput(" 5 ", 10))
        assertNull(parsePageInput("0", 10))
        assertNull(parsePageInput("11", 10))
        assertNull(parsePageInput("abc", 10))
    }

    @Test fun hexColours() {
        assertEquals(0xFFFFFF98.toInt(), hexToArgb("#FFFF98"))
        assertEquals(0xFF112233.toInt(), hexToArgb("112233"))
        assertNull(hexToArgb("#12"))
        assertNull(hexToArgb("#GGGGGG"))
        assertEquals("#FFFF98", argbToHex(0xFFFFFF98.toInt()))
    }

    @Test fun highlightsAreSortedInReadingOrder() {
        val sorted = sortHighlights(listOf(hl(3, 2, 0.1f), hl(1, 1, 0.5f), hl(2, 1, 0.2f), hl(4, 1, 0.2f, 0.5f)))
        assertEquals(listOf(2L, 4L, 1L, 3L), sorted.map { it.id })
    }

    @Test fun neighbourHighlightWraps() {
        val s = sortHighlights(listOf(hl(1, 1), hl(2, 2), hl(3, 3)))
        assertEquals(2L, neighbourHighlight(s, 1L, true)?.id)
        assertEquals(1L, neighbourHighlight(s, 3L, true)?.id)
        assertEquals(3L, neighbourHighlight(s, 1L, false)?.id)
        assertEquals(1L, neighbourHighlight(s, null, true)?.id)
        assertEquals(3L, neighbourHighlight(s, null, false)?.id)
        assertNull(neighbourHighlight(emptyList(), 1L, true))
    }

    @Test fun outlineShowsChildrenOnlyWhenExpanded() {
        val tree = listOf(
            OutlineNode("a", "A", 0, listOf(OutlineNode("a1", "A1", 1, emptyList()))),
            OutlineNode("b", "B", 2, emptyList()),
        )
        assertEquals(listOf("a", "b"), flattenOutline(tree, emptySet()).map { it.node.id })
        val open = flattenOutline(tree, setOf("a"))
        assertEquals(listOf("a", "a1", "b"), open.map { it.node.id })
        assertEquals(listOf(0, 1, 0), open.map { it.depth })
        assertTrue(AUTO_SCROLL_PRESETS.all { it in MIN_AUTO_SCROLL_PX..MAX_AUTO_SCROLL_PX })
    }

    @Test fun doodleFileNamesAreUniqueAndSafe() {
        assertEquals("Report (doodle).pdf", doodleFileName("Report") { false })
        assertEquals("Report (doodle 2).pdf", doodleFileName("Report") { it == "Report (doodle).pdf" })
        assertEquals("Report (doodle 3).pdf", doodleFileName("Report") { it == "Report (doodle).pdf" || it == "Report (doodle 2).pdf" })
        assertEquals("a_b (doodle).pdf", doodleFileName("a/b") { false })
        assertEquals("Document (doodle).pdf", doodleFileName("  ") { false })
    }

    @Test fun doodleColoursAreOpaqueAndDistinct() {
        assertEquals(DOODLE_COLORS.size, DOODLE_COLORS.toSet().size)
        assertTrue(DOODLE_COLORS.all { (it.toLong() and 0xFF000000L) == 0xFF000000L })
    }
}
