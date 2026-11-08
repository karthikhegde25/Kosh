package com.kosh.android

import com.kosh.android.pdflist.DeleteOutcome
import com.kosh.android.pdflist.FileOperations
import com.kosh.android.pdflist.RenameOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileOperationsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun pdf(name: String, content: String = "%PDF-1.4 test"): File =
        tmp.newFile(name).also { it.writeText(content) }

    @Test
    fun renameKeepsContentAndExtension() {
        val src = pdf("old.pdf", "hello")
        val result = FileOperations.rename(src, "new name")
        assertTrue(result is RenameOutcome.Renamed)
        val dst = (result as RenameOutcome.Renamed).file
        assertEquals("new name.pdf", dst.name)
        assertEquals("hello", dst.readText())
        assertFalse(src.exists())
        assertEquals(src.parentFile, dst.parentFile) // stays in the same folder
    }

    @Test
    fun renameDoesNotDoubleTheExtension() {
        val src = pdf("a.pdf")
        val result = FileOperations.rename(src, "b.pdf") as RenameOutcome.Renamed
        assertEquals("b.pdf", result.file.name)
    }

    @Test
    fun renameNeverOverwritesAnExistingFile() {
        val src = pdf("one.pdf", "ONE")
        val other = pdf("two.pdf", "TWO")
        assertEquals(RenameOutcome.NameExists, FileOperations.rename(src, "two"))
        assertEquals("ONE", src.readText())
        assertEquals("TWO", other.readText())
    }

    @Test
    fun renameRejectsInvalidNames() {
        val src = pdf("x.pdf")
        for (bad in listOf("", "   ", "a/b", "a:b", "what?", "..", "name.", "a\u0000b")) {
            assertEquals("input '$bad'", RenameOutcome.InvalidName, FileOperations.rename(src, bad))
        }
        assertTrue(src.exists())
    }

    @Test
    fun renameToSameNameIsUnchanged() {
        val src = pdf("same.pdf")
        assertTrue(FileOperations.rename(src, "same") is RenameOutcome.Unchanged)
        assertTrue(src.exists())
    }

    @Test
    fun renameMissingFileReportsNotFound() {
        val ghost = File(tmp.root, "ghost.pdf")
        assertEquals(RenameOutcome.NotFound, FileOperations.rename(ghost, "anything"))
    }

    @Test
    fun renameSupportsSpacesAndPunctuation() {
        val src = pdf("a.pdf")
        val result = FileOperations.rename(src, "Report (final) 2026 - v2") as RenameOutcome.Renamed
        assertEquals("Report (final) 2026 - v2.pdf", result.file.name)
        assertTrue(result.file.exists())
    }

    @Test
    fun deleteRemovesTheFile() {
        val f = pdf("gone.pdf")
        assertEquals(DeleteOutcome.Deleted, FileOperations.delete(f))
        assertFalse(f.exists())
    }

    @Test
    fun deleteOfMissingFileIsAlreadyGone() {
        assertEquals(DeleteOutcome.AlreadyGone, FileOperations.delete(File(tmp.root, "nope.pdf")))
    }
}
