package com.kosh.android.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeKindTest {
    @Test fun kindsComeFromTheExtension() {
        assertEquals(FileKind.PDF, FileKind.fromFileName("a.PDF"))
        assertEquals(FileKind.DOCX, FileKind.fromFileName("My Report.docx"))
        assertEquals(FileKind.XLSX, FileKind.fromFileName("sheet.XLSX"))
        assertEquals(FileKind.PPTX, FileKind.fromFileName("deck.pptx"))
        assertNull(FileKind.fromFileName("old.doc"))
        assertNull(FileKind.fromFileName("noext"))
    }

    @Test fun lockStubsAndHiddenFilesAreIgnored() {
        assertNull(FileKind.fromFileName("~\$Report.docx"))
        assertNull(FileKind.fromFileName(".hidden.pdf"))
    }

    @Test fun officeFlag() {
        assertTrue(!FileKind.PDF.isOffice && FileKind.DOCX.isOffice && FileKind.XLSX.isOffice && FileKind.PPTX.isOffice)
        assertEquals(3, FileKind.officeMimeTypes.size)
    }

    @Test fun uniquePdfNames() {
        assertEquals("Report.pdf", uniquePdfName("Report.docx") { false })
        assertEquals("Report (1).pdf", uniquePdfName("Report.docx") { it == "Report.pdf" })
        assertEquals("Report (2).pdf", uniquePdfName("Report.docx") { it == "Report.pdf" || it == "Report (1).pdf" })
        assertEquals("Document.pdf", uniquePdfName(".docx") { false })
    }
}
