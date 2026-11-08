package com.kosh.android

import com.kosh.android.pdflist.PdfFile
import com.kosh.android.pdflist.filterFiles
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSearchTest {
    private fun f(name: String, folder: String = "") = PdfFile("/s/$folder/$name", name, folder, 1, 0)
    private val files = listOf(f("Tax Return 2025.pdf", "Documents"), f("Budget.xlsx", "Work"), f("Holiday Plan.docx", "Download"))

    @Test fun blankQueryKeepsEverything() = assertEquals(files, filterFiles(files, "  "))
    @Test fun matchesNameIgnoringCase() = assertEquals(listOf(files[0]), filterFiles(files, "TAX"))
    @Test fun matchesFolder() = assertEquals(listOf(files[1]), filterFiles(files, "work"))
    @Test fun everyWordMustMatch() {
        assertEquals(listOf(files[2]), filterFiles(files, "holiday docx"))
        assertEquals(emptyList<PdfFile>(), filterFiles(files, "holiday tax"))
    }
}
