package com.kosh.android.notes

import com.kosh.android.notes.data.NoteImageStore
import com.kosh.android.notes.format.NoteFormatConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NoteImageStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store() = NoteImageStore(tmp.newFolder("note_images"))

    @Test
    fun imagesAreListedInTheOrderTheyWereAdded() {
        val s = store()
        val a = s.newFile("note-1").also { it.writeText("a") }
        Thread.sleep(3)
        val b = s.newFile("note-1").also { it.writeText("b") }
        Thread.sleep(3)
        val c = s.newFile("note-1").also { it.writeText("c") }
        assertEquals(listOf(a, b, c).map { it.name }, s.list("note-1").map { it.name })
    }

    @Test
    fun notesHaveSeparateImageFolders() {
        val s = store()
        s.newFile("one").writeText("x")
        assertEquals(1, s.list("one").size)
        assertTrue(s.list("two").isEmpty())
    }

    @Test
    fun removingAndDeletingAll() {
        val s = store()
        val f = s.newFile("n").also { it.writeText("x") }
        s.newFile("n").writeText("y")
        assertTrue(s.remove(f))
        assertEquals(1, s.list("n").size)
        s.deleteAll("n")
        assertTrue(s.list("n").isEmpty())
    }

    @Test
    fun duplicatingANoteCopiesItsImagesInOrder() {
        val s = store()
        val first = s.newFile("orig").also { it.writeText("1") }
        Thread.sleep(3)
        s.newFile("orig").writeText("2")
        assertEquals(2, s.copyAll("orig", "copy"))
        val copies = s.list("copy")
        assertEquals(listOf("1", "2"), copies.map { it.readText() })
        assertTrue(first.exists()) // the original is untouched
    }

    @Test(expected = IllegalArgumentException::class)
    fun idsThatCouldEscapeTheFolderAreRejected() {
        store().newFile("../evil")
    }

    @Test
    fun textAndMarkdownExportsNeverMentionImages() {
        // Images are separate files; the converters only see title and text.
        val md = NoteFormatConverter.toMarkdown("Trip", "Packing list\n- passport")
        val txt = NoteFormatConverter.toPlainText("Trip", "Packing list\n- passport")
        for (out in listOf(md, txt)) {
            assertFalse(out.contains(".jpg"))
            assertFalse(out.contains("!["))
        }
    }
}
