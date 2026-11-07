package com.kosh.android

import com.kosh.android.util.MAX_TITLE_LENGTH
import com.kosh.android.util.clampTitle
import com.kosh.android.util.defaultTitle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TitleUtilsTest {
    @Test
    fun defaultTitleUsesIsoDate() {
        assertEquals("Scan_2026-10-13", defaultTitle(LocalDate.of(2026, 10, 13)))
    }

    @Test
    fun clampTitleLimitsLength() {
        assertEquals(MAX_TITLE_LENGTH, clampTitle("a".repeat(500)).length)
        assertEquals("Invoice ñ 2026", clampTitle("Invoice ñ 2026"))
    }
}
