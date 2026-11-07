package com.kosh.android

import com.kosh.android.pdflist.FileRowData
import com.kosh.android.pdflist.FolderRowData
import com.kosh.android.pdflist.PdfFile
import com.kosh.android.pdflist.buildFolderTree
import com.kosh.android.pdflist.flattenTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeTest {
    private val root = "/storage/emulated/0"

    private fun f(rel: String) = PdfFile("$root/$rel", rel.substringAfterLast('/'), "", 1000, 0)

    private val files = listOf(
        f("top.pdf"),
        f("Download/a.pdf"),
        f("Download/Work/b.pdf"),
        f("Download/Work/2026/c.pdf"),
        f("Documents/d.pdf"),
    )

    @Test
    fun subfoldersStayNestedUnderTheirParents() {
        val tree = buildFolderTree(files, root, "Internal storage")
        assertEquals(setOf("Download", "Documents"), tree.folders.keys)
        val download = tree.folders.getValue("Download")
        assertEquals(setOf("Work"), download.folders.keys)
        val work = download.folders.getValue("Work")
        assertEquals("Download/Work", work.relPath)
        assertEquals(listOf("b.pdf"), work.files.map { it.name })
        assertEquals("Download/Work/2026", work.folders.getValue("2026").relPath)
    }

    @Test
    fun countsIncludeEverythingBelow() {
        val tree = buildFolderTree(files, root, "Internal storage")
        assertEquals(5, tree.totalCount)
        assertEquals(3, tree.folders.getValue("Download").totalCount)
        assertEquals(2, tree.folders.getValue("Download").folders.getValue("Work").totalCount)
    }

    @Test
    fun rootLevelPdfsLiveInTheRootNode() {
        val tree = buildFolderTree(files, root, "Internal storage")
        assertEquals(listOf("top.pdf"), tree.files.map { it.name })
    }

    @Test
    fun collapsedFoldersHideTheirContents() {
        val tree = buildFolderTree(files, root, "Internal storage")
        val rows = flattenTree(tree, setOf(""))
        // root + 2 folders + top.pdf; nothing inside Download or Documents yet
        assertEquals(4, rows.size)
        assertTrue(rows.first() is FolderRowData)
        assertTrue(rows.drop(1).filterIsInstance<FolderRowData>().none { it.expanded })
    }

    @Test
    fun expandingShowsNestedContentWithIncreasingDepth() {
        val tree = buildFolderTree(files, root, "Internal storage")
        val rows = flattenTree(tree, setOf("", "Download", "Download/Work", "Download/Work/2026"))
        val c = rows.filterIsInstance<FileRowData>().first { it.file.name == "c.pdf" }
        val b = rows.filterIsInstance<FileRowData>().first { it.file.name == "b.pdf" }
        val a = rows.filterIsInstance<FileRowData>().first { it.file.name == "a.pdf" }
        assertEquals(4, c.depth) // root(0) > Download(1) > Work(2) > 2026(3) > file(4)
        assertEquals(3, b.depth)
        assertEquals(2, a.depth)
        // Folders sort before the files of their parent, files are listed after subfolders.
        val names = rows.map { if (it is FolderRowData) "[" + it.node.name + "]" else (it as FileRowData).file.name }
        assertTrue(names.indexOf("[Work]") < names.indexOf("a.pdf"))
    }

    @Test
    fun deletingTheLastPdfRemovesItsFolder() {
        val remaining = files.filter { it.name != "d.pdf" }
        val tree = buildFolderTree(remaining, root, "Internal storage")
        assertEquals(setOf("Download"), tree.folders.keys)
    }
}
