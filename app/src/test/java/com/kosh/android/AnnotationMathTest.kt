package com.kosh.android

import com.kosh.android.data.db.AnnotationType
import com.kosh.android.data.model.NormalizedPoint
import com.kosh.android.util.AnnotationCodec
import com.kosh.android.util.DrawItem
import com.kosh.android.util.distanceToSegment
import com.kosh.android.util.hitTest
import com.kosh.android.util.normalizePoint
import com.kosh.android.util.thinPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationMathTest {

    private fun p(x: Float, y: Float) = NormalizedPoint(x, y)

    @Test
    fun codecRoundTripsPoints() {
        val points = listOf(p(0.1f, 0.2f), p(0.5f, 0.25f), p(1f, 0f))
        val decoded = AnnotationCodec.decode(AnnotationCodec.encode(points))
        assertEquals(points.size, decoded.size)
        points.zip(decoded).forEach { (a, b) ->
            assertEquals(a.x, b.x, 0.0001f)
            assertEquals(a.y, b.y, 0.0001f)
        }
    }

    @Test
    fun decodeSkipsGarbageAndClampsValues() {
        val decoded = AnnotationCodec.decode("0.5,0.5;oops;1.7,-3;1,2,3;;NaN,1")
        assertEquals(listOf(p(0.5f, 0.5f), p(1f, 0f)), decoded)
        assertTrue(AnnotationCodec.decode("").isEmpty())
    }

    @Test
    fun touchPositionsBecomePageCoordinates() {
        assertEquals(p(0.5f, 0.25f), normalizePoint(500f, 250f, 1000f, 1000f))
        assertEquals(p(1f, 0f), normalizePoint(1500f, -20f, 1000f, 1000f)) // clamped
        assertEquals(p(0f, 0f), normalizePoint(10f, 10f, 0f, 0f)) // no size yet
    }

    @Test
    fun segmentDistance() {
        assertEquals(0f, distanceToSegment(5f, 0f, 0f, 0f, 10f, 0f), 0.001f)
        assertEquals(3f, distanceToSegment(5f, 3f, 0f, 0f, 10f, 0f), 0.001f)
        assertEquals(5f, distanceToSegment(13f, 4f, 0f, 0f, 10f, 0f), 0.001f) // beyond the end: distance to the endpoint
        assertEquals(5f, distanceToSegment(3f, 4f, 0f, 0f, 0f, 0f), 0.001f) // degenerate segment
    }

    @Test
    fun thinningKeepsEndsAndDropsNearDuplicates() {
        val dense = (0..100).map { p(it / 10000f, 0.5f) } // spacing 0.0001, far below the threshold
        val thin = thinPoints(dense, 0.0015f)
        assertEquals(dense.first(), thin.first())
        assertEquals(dense.last(), thin.last())
        assertTrue(thin.size < 15)
        assertEquals(2, thinPoints(listOf(p(0f, 0f), p(1f, 1f))).size)
    }

    private val stroke = DrawItem("s", AnnotationType.PEN, 0xFF000000, 0.01f, listOf(p(0.1f, 0.5f), p(0.9f, 0.5f)), null)
    private val note = DrawItem("n", AnnotationType.TEXT, 0xFFFFEB3B, 0.03f, listOf(p(0.2f, 0.2f)), "hello")

    @Test
    fun tapsNearAStrokeHitIt() {
        assertEquals("s", hitTest(listOf(stroke), 500f, 505f, 1000f, 1000f, tolerancePx = 8f)?.id)
        assertNull(hitTest(listOf(stroke), 500f, 530f, 1000f, 1000f, tolerancePx = 8f))
    }

    @Test
    fun tapsInsideANoteHitIt() {
        assertNotNull(hitTest(listOf(note), 400f, 250f, 1000f, 1000f, tolerancePx = 8f))
        assertNull(hitTest(listOf(note), 900f, 900f, 1000f, 1000f, tolerancePx = 8f))
    }

    @Test
    fun theTopmostAnnotationWinsWhenTheyOverlap() {
        val under = stroke.copy(id = "under")
        val over = stroke.copy(id = "over")
        assertEquals("over", hitTest(listOf(under, over), 500f, 500f, 1000f, 1000f, 8f)?.id)
    }
}
