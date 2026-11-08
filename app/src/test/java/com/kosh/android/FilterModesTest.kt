package com.kosh.android

import com.kosh.android.data.model.FilterMode
import com.kosh.android.util.FilterMatrices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterModesTest {

    @Test fun everyModeExceptOriginalChangesPixels() {
        FilterMode.entries.forEach { mode ->
            val m = FilterMatrices.forMode(mode)
            if (mode == FilterMode.ORIGINAL) assertNull(m) else {
                assertNotNull(mode.name, m)
                assertEquals(20, m!!.size)
            }
        }
    }

    @Test fun invertFlipsBlackAndWhite() {
        val m = FilterMatrices.forMode(FilterMode.INVERT)!!
        assertEquals(Triple(0, 0, 0), FilterMatrices.apply(m, 255, 255, 255))
        assertEquals(Triple(255, 255, 255), FilterMatrices.apply(m, 0, 0, 0))
    }

    @Test fun lightenBrightensMidGrey() {
        val m = FilterMatrices.forMode(FilterMode.LIGHTEN)!!
        val (r, g, b) = FilterMatrices.apply(m, 100, 100, 100)
        assertTrue(r > 100 && g > 100 && b > 100)
    }

    @Test fun sepiaWarmsWhite() {
        val m = FilterMatrices.forMode(FilterMode.SEPIA)!!
        val (r, _, b) = FilterMatrices.apply(m, 255, 255, 255)
        assertEquals(255, r)
        assertTrue(b < 255)
    }
}
