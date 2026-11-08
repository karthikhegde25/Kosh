package com.kosh.android

import com.kosh.android.util.buildRenamedFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileNamesTest {
    @Test
    fun keepsExtensionAndTrims() {
        assertEquals("Report.pdf", buildRenamedFileName("  Report  ", "pdf"))
        assertEquals("Report.PDF", buildRenamedFileName("Report", "PDF"))
    }

    @Test
    fun doesNotDuplicateTypedExtension() {
        assertEquals("Report.pdf", buildRenamedFileName("Report.pdf", "pdf"))
        assertEquals("Report.pdf", buildRenamedFileName("Report.PDF", "pdf"))
        assertEquals("a.b.pdf", buildRenamedFileName("a.b", "pdf"))
    }

    @Test
    fun rejectsBadNames() {
        for (bad in listOf("", " ", ".", "..", "a/b", "a\\b", "a*b", "a|b", "a<b", "a\"b", "x.", ".pdf")) {
            assertNull("input '$bad'", buildRenamedFileName(bad, "pdf"))
        }
    }

    @Test
    fun rejectsNamesOverTheFileSystemLimit() {
        assertNull(buildRenamedFileName("a".repeat(300), "pdf"))
        assertEquals(255, buildRenamedFileName("a".repeat(251), "pdf")!!.length)
    }
}
