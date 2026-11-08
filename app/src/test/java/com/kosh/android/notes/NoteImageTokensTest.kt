package com.kosh.android.notes

import com.kosh.android.notes.format.ImagePiece
import com.kosh.android.notes.format.NoteImageTokens
import com.kosh.android.notes.format.TextPiece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteImageTokensTest {

    private val t1 = NoteImageTokens.token("a.jpg")
    private val t2 = NoteImageTokens.token("b.jpg")

    @Test fun recognisesOnlyWholeImageLines() {
        assertEquals("a.jpg", NoteImageTokens.nameOf(t1))
        assertNull(NoteImageTokens.nameOf("text $t1"))
        assertNull(NoteImageTokens.nameOf("![x](https://example.com/a.jpg)"))
        assertNull(NoteImageTokens.nameOf("![image](kosh-image:../a.jpg)"))
        assertEquals(setOf("a.jpg", "b.jpg"), NoteImageTokens.referencedNames("x\n$t1\ny\n$t2"))
    }

    @Test fun textWithoutImagesIsUntouched() {
        assertEquals("hello\nworld", NoteImageTokens.normalize("hello\nworld"))
        assertEquals("hello", NoteImageTokens.stripImages("hello"))
        assertFalse(NoteImageTokens.hasImages("hello"))
    }

    @Test fun insertingInTheMiddleSplitsTheLine() {
        val (text, caret) = NoteImageTokens.insertAt("Hello world", 5, "a.jpg")
        assertEquals("Hello\n$t1\n world", text)
        assertEquals(text.indexOf(" world"), caret)
    }

    @Test fun insertingAtStartAndEndKeepsTextLinesAroundTheImage() {
        val (atStart, c1) = NoteImageTokens.insertAt("Hi", 0, "a.jpg")
        assertEquals("\n$t1\nHi", atStart)
        assertEquals(atStart.indexOf("Hi"), c1)
        val (atEnd, c2) = NoteImageTokens.insertAt("Hi", 2, "a.jpg")
        assertEquals("Hi\n$t1\n", atEnd)
        assertEquals(atEnd.length, c2)
        val (empty, _) = NoteImageTokens.insertAt("", 0, "a.jpg")
        assertEquals("\n$t1\n", empty)
    }

    @Test fun insertingAtTheStartOfALineDoesNotAddBlankLines() {
        val (text, _) = NoteImageTokens.insertAt("One\nTwo", 4, "a.jpg")
        assertEquals("One\n$t1\nTwo", text)
    }

    @Test fun twoImagesNeverTouch() {
        val (first, caret) = NoteImageTokens.insertAt("Hi", 2, "a.jpg")
        val (second, _) = NoteImageTokens.insertAt(first, caret, "b.jpg")
        val lines = second.split('\n')
        for (i in 0 until lines.size - 1) {
            assertFalse(NoteImageTokens.nameOf(lines[i]) != null && NoteImageTokens.nameOf(lines[i + 1]) != null)
        }
        assertEquals(setOf("a.jpg", "b.jpg"), NoteImageTokens.referencedNames(second))
    }

    @Test fun splitGivesTextImageTextWithOffsets() {
        val content = "Intro\nmore\n$t1\nOutro"
        val pieces = NoteImageTokens.split(content)
        assertEquals(3, pieces.size)
        val a = pieces[0] as TextPiece
        assertEquals("Intro\nmore", a.text); assertEquals(0, a.start); assertEquals(0, a.firstLine); assertEquals(10, a.end)
        val img = pieces[1] as ImagePiece
        assertEquals("a.jpg", img.name); assertEquals(11, img.start); assertEquals(11 + t1.length, img.end)
        val b = pieces[2] as TextPiece
        assertEquals("Outro", b.text); assertEquals(3, b.firstLine); assertEquals(content.indexOf("Outro"), b.start)
    }

    @Test fun editingATextPieceInPlaceKeepsEverythingElse() {
        val content = "Intro\n$t1\nOutro"
        val pieces = NoteImageTokens.split(content)
        val last = pieces.last() as TextPiece
        val edited = content.replaceRange(last.start, last.end, "Outro and more")
        assertEquals("Intro\n$t1\nOutro and more", edited)
        assertEquals(3, NoteImageTokens.split(edited).size)
    }

    @Test fun removingAnImageJoinsTheTextAroundIt() {
        assertEquals("Intro\nOutro", NoteImageTokens.removeImage("Intro\n$t1\nOutro", "a.jpg"))
        assertEquals("Intro\n\n$t2\nOutro", NoteImageTokens.removeImage("Intro\n$t1\n\n$t2\nOutro", "a.jpg"))
        assertEquals("x", NoteImageTokens.removeImage("x", "a.jpg"))
    }

    @Test fun oldAttachmentsAreAddedAtTheTopOnce() {
        val adopted = NoteImageTokens.adoptUnreferenced("Body", listOf("a.jpg", "b.jpg"))
        assertEquals(setOf("a.jpg", "b.jpg"), NoteImageTokens.referencedNames(adopted))
        assertTrue(adopted.endsWith("Body"))
        assertEquals(adopted, NoteImageTokens.adoptUnreferenced(adopted, listOf("a.jpg", "b.jpg")))
        assertEquals("Body", NoteImageTokens.adoptUnreferenced("Body", emptyList()))
    }

    @Test fun exportsDropImageLinesWithoutExtraGaps() {
        assertEquals("Intro\nOutro", NoteImageTokens.stripImages("Intro\n$t1\nOutro"))
        assertEquals("Intro\n\nOutro", NoteImageTokens.stripImages("Intro\n\n$t1\n\nOutro").replace("\n\n\n", "\n\n"))
        assertEquals("", NoteImageTokens.stripImages("\n$t1\n").trim())
    }
}
