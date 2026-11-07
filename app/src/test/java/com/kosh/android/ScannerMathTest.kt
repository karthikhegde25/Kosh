package com.kosh.android

import com.kosh.android.data.model.CropCorners
import com.kosh.android.data.model.FilterMode
import com.kosh.android.data.model.NormalizedPoint
import com.kosh.android.util.FilterMatrices
import com.kosh.android.util.defaultCorners
import com.kosh.android.util.fullImageCorners
import com.kosh.android.util.isFullImage
import com.kosh.android.util.isValidCorners
import com.kosh.android.util.moveItem
import com.kosh.android.util.normalizeRotation
import com.kosh.android.util.outputSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerMathTest {

    private fun p(x: Float, y: Float) = NormalizedPoint(x, y)

    @Test
    fun defaultCornersAreValid() {
        assertTrue(isValidCorners(defaultCorners()))
        assertTrue(isValidCorners(fullImageCorners()))
        assertTrue(fullImageCorners().isFullImage())
        assertFalse(defaultCorners().isFullImage())
    }

    @Test
    fun crossedOrCollapsedCornersAreInvalid() {
        val c = defaultCorners()
        assertFalse(isValidCorners(c.copy(topLeft = p(0.9f, 0.04f)))) // top-left right of top-right
        assertFalse(isValidCorners(c.copy(bottomLeft = p(0.04f, 0.02f)))) // bottom-left above top-left
        assertFalse(isValidCorners(c.copy(topRight = p(0.07f, 0.04f)))) // gap too small
        assertFalse(isValidCorners(c.copy(topLeft = p(-0.1f, 0.04f)))) // outside the image
    }

    @Test
    fun outputSizeUsesTheLongerOppositeEdges() {
        assertEquals(1000 to 500, outputSize(fullImageCorners(), 1000, 500))
        // A trapezoid: top edge 600 wide, bottom edge 1000 wide. Each slanted side is sqrt(200^2 + 500^2) = 538 long.
        val trapezoid = CropCorners(p(0.2f, 0f), p(0.8f, 0f), p(1f, 1f), p(0f, 1f))
        assertEquals(1000 to 538, outputSize(trapezoid, 1000, 500))
    }

    @Test
    fun moveItemShiftsWithinBoundsOnly() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), moveItem(list, 0, 1))
        assertEquals(listOf("a", "c", "b"), moveItem(list, 2, -1))
        assertEquals(list, moveItem(list, 0, -1)) // already first
        assertEquals(list, moveItem(list, 2, 1)) // already last
        assertEquals(list, moveItem(list, 5, 1)) // bad index
    }

    @Test
    fun rotationIsNormalised() {
        assertEquals(270, normalizeRotation(-90))
        assertEquals(90, normalizeRotation(450))
        assertEquals(0, normalizeRotation(360))
    }

    @Test
    fun originalFilterLeavesPixelsAlone() {
        assertNull(FilterMatrices.forMode(FilterMode.ORIGINAL))
    }

    @Test
    fun grayscaleGivesEqualChannels() {
        val m = FilterMatrices.forMode(FilterMode.GRAYSCALE)!!
        val (r, g, b) = FilterMatrices.apply(m, 255, 0, 0)
        assertEquals(r, g)
        assertEquals(g, b)
        assertEquals(54, r)
    }

    @Test
    fun blackAndWhiteIsStrictlyTwoTone() {
        val m = FilterMatrices.forMode(FilterMode.BLACK_AND_WHITE)!!
        assertEquals(Triple(0, 0, 0), FilterMatrices.apply(m, 60, 60, 60))
        assertEquals(Triple(255, 255, 255), FilterMatrices.apply(m, 200, 200, 200))
        assertEquals(Triple(0, 0, 0), FilterMatrices.apply(m, 20, 40, 30))
    }

    @Test
    fun documentFilterKeepsWhitePaperWhiteAndDarkensText() {
        val m = FilterMatrices.forMode(FilterMode.DOCUMENT)!!
        assertEquals(Triple(255, 255, 255), FilterMatrices.apply(m, 255, 255, 255))
        val (r, _, _) = FilterMatrices.apply(m, 90, 90, 90)
        assertTrue("text gets darker", r < 90)
    }

    @Test
    fun multiplyAppliesFirstThenSecond() {
        val identity = FilterMatrices.contrast(1f, 0f)
        val sat = FilterMatrices.saturation(0f)
        val combined = FilterMatrices.multiply(sat, identity)
        for (i in 0 until 20) assertEquals(sat[i], combined[i], 0.0001f)
        // brighten by +10, then double the contrast around 128
        val brighten = FilterMatrices.contrast(1f, 10f)
        val double = FilterMatrices.contrast(2f, 0f)
        val (r, _, _) = FilterMatrices.apply(FilterMatrices.multiply(brighten, double), 100, 100, 100)
        assertEquals(((100 + 10) - 128) * 2 + 128, r)
    }
}
