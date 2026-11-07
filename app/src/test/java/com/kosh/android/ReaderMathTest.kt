package com.kosh.android

import com.kosh.android.util.MAX_RENDER_PIXELS
import com.kosh.android.util.MAX_ZOOM
import com.kosh.android.util.MIN_ZOOM
import com.kosh.android.util.bucketWidth
import com.kosh.android.util.clampPage
import com.kosh.android.util.clampZoom
import com.kosh.android.util.computeRenderSize
import com.kosh.android.util.documentKey
import com.kosh.android.util.fitPageZoom
import com.kosh.android.util.formatFileSize
import com.kosh.android.util.maxPanX
import com.kosh.android.util.parsePageInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMathTest {

    @Test
    fun parsePageInputValidatesRange() {
        assertEquals(0, parsePageInput("1", 10))
        assertEquals(9, parsePageInput(" 10 ", 10))
        assertNull(parsePageInput("0", 10))
        assertNull(parsePageInput("11", 10))
        assertNull(parsePageInput("", 10))
        assertNull(parsePageInput("abc", 10))
        assertNull(parsePageInput("-3", 10))
    }

    @Test
    fun clampPageStaysInBounds() {
        assertEquals(0, clampPage(-5, 10))
        assertEquals(9, clampPage(42, 10)) // saved page beyond a shortened PDF
        assertEquals(4, clampPage(4, 10))
        assertEquals(0, clampPage(3, 0))
    }

    @Test
    fun zoomIsClamped() {
        assertEquals(MIN_ZOOM, clampZoom(0.01f), 0f)
        assertEquals(MAX_ZOOM, clampZoom(99f), 0f)
        assertEquals(1.5f, clampZoom(1.5f), 0f)
    }

    @Test
    fun fitPageZoomNeverExceedsFitWidth() {
        // A4 portrait in a tall viewport: width-limited, so zoom stays 1.
        assertEquals(1f, fitPageZoom(1000f, 2400f, 595f, 842f), 0.001f)
        // A4 portrait in a short viewport: must shrink to fit the height.
        val z = fitPageZoom(1000f, 700f, 595f, 842f)
        assertTrue(z < 1f)
        assertEquals(700f, 1000f * z * 842f / 595f, 1f)
    }

    @Test
    fun panLimitIsZeroUnlessZoomedIn() {
        assertEquals(0f, maxPanX(1000f, 1f), 0f)
        assertEquals(0f, maxPanX(1000f, 0.5f), 0f)
        assertEquals(500f, maxPanX(1000f, 2f), 0f)
    }

    @Test
    fun renderSizeKeepsAspectRatioAndPixelBudget() {
        val (w, h) = computeRenderSize(595, 842, 1080)
        assertEquals(1080, w)
        assertEquals(1528, h)

        // Huge zoom request on a tall page is capped by the pixel budget, aspect ratio preserved.
        val (w2, h2) = computeRenderSize(595, 842, 2560)
        assertTrue(w2.toLong() * h2 <= MAX_RENDER_PIXELS + 2000)
        assertEquals(842.0 / 595.0, h2.toDouble() / w2, 0.01)
    }

    @Test
    fun bucketWidthRoundsUp() {
        assertEquals(1152, bucketWidth(1080f))
        assertEquals(128, bucketWidth(1f))
    }

    @Test
    fun documentKeyIsStableAndUriBased() {
        val a = documentKey("content://docs/1/report.pdf")
        assertEquals(a, documentKey("content://docs/1/report.pdf"))
        // Same display name, different location => different key.
        assertNotEquals(a, documentKey("content://docs/2/report.pdf"))
    }

    @Test
    fun fileSizeFormatting() {
        assertEquals("512 B", formatFileSize(512))
        assertEquals("2 KB", formatFileSize(2048))
        assertTrue(formatFileSize(5L * 1024 * 1024).endsWith("MB"))
    }
}

class ZoomAnchorTest {
    private val gap = 10f

    @Test
    fun theSpotUnderThePinchStaysPut() {
        // Three pages, now 200 tall after zooming. The pinch was in the middle of page 1, 150 below the content start.
        val pos = com.kosh.android.util.scrollAfterZoom(listOf(200f, 200f, 200f), gap, anchorIndex = 1, fraction = 0.5f, focalInContent = 150f)
        // The point is 310 into the document (210 + 100); 150 of it must be visible above it, so we scroll to 160.
        assertEquals(0, pos.index)
        assertEquals(160, pos.offset)
    }

    @Test
    fun scrollingNeverGoesAboveTheTop() {
        val pos = com.kosh.android.util.scrollAfterZoom(listOf(200f, 200f), gap, 0, 0.1f, focalInContent = 500f)
        assertEquals(com.kosh.android.util.ScrollPosition(0, 0), pos)
    }

    @Test
    fun theResultMapsToTheRightPage() {
        // Anchor near the bottom of page 2 with the pinch at the top of the screen: scrolls well into the document.
        val pos = com.kosh.android.util.scrollAfterZoom(listOf(100f, 100f, 100f, 100f), gap, 2, 0.9f, focalInContent = 0f)
        // Point is at 2*110 + 90 = 310; page 2 starts at 220, so index 2 with offset 90.
        assertEquals(2, pos.index)
        assertEquals(90, pos.offset)
    }

    @Test
    fun anAnchorAtTheVeryTopStaysAtTheTop() {
        val pos = com.kosh.android.util.scrollAfterZoom(listOf(300f, 300f), gap, 0, 0f, focalInContent = 0f)
        assertEquals(com.kosh.android.util.ScrollPosition(0, 0), pos)
    }

    @Test
    fun emptyDocumentIsSafe() {
        assertEquals(com.kosh.android.util.ScrollPosition(0, 0), com.kosh.android.util.scrollAfterZoom(emptyList(), gap, 0, 0f, 0f))
    }
}
