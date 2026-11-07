package com.kosh.android.notes

import com.kosh.android.notes.format.NoteFormatConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFormatConverterTest {

    @Test
    fun markdownAddsTitleAsHeading() {
        assertEquals("# My note\n\nHello\n- [x] done\n", NoteFormatConverter.toMarkdown("My note", "Hello\n- [x] done"))
    }

    @Test
    fun markdownWithoutTitleIsJustTheBody() {
        assertEquals("text\n", NoteFormatConverter.toMarkdown("", "text"))
        assertEquals("a\nb\n", NoteFormatConverter.toMarkdown("  ", "a\r\nb"))
    }

    @Test
    fun markdownDoesNotRepeatATitleAlreadyInTheBody() {
        assertEquals("# Plan\nbody\n", NoteFormatConverter.toMarkdown("Plan", "# Plan\nbody"))
    }

    @Test
    fun markdownKeepsBlankLinesAndFormatting() {
        val body = "one\n\n**two**\n\n  - nested"
        assertEquals("$body\n", NoteFormatConverter.toMarkdown("", body))
    }

    @Test
    fun plainTextRemovesSyntaxButKeepsWordsAndOrder() {
        val md = "# Head\n**bold** and *it* and ~~gone~~ and `code`\n- one\n  - two\n1. first\n- [ ] a\n- [x] b"
        val expected = "T\n\nHead\nbold and it and gone and code\n- one\n  - two\n1. first\n[ ] a\n[x] b\n"
        assertEquals(expected, NoteFormatConverter.toPlainText("T", md))
    }

    @Test
    fun plainTextHasNoMarkdownControlCharactersForSimpleNotes() {
        val out = NoteFormatConverter.toPlainText("Title", "# H\n**b** *i*")
        assertFalse(out.contains("#"))
        assertFalse(out.contains("*"))
    }

    @Test
    fun everyWordSurvivesConversion() {
        val md = "# alpha\nbeta **gamma** *delta*\n- epsilon\n- [x] zeta\n1. eta"
        val plain = NoteFormatConverter.toPlainText("", md)
        for (word in listOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta")) {
            assertTrue("missing $word", plain.contains(word))
        }
        // order is preserved
        val positions = listOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta").map { plain.indexOf(it) }
        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun unicodeAndEmojiPassThrough() {
        val text = "Привет мир — 你好 🙂"
        assertTrue(NoteFormatConverter.toPlainText("", text).contains(text))
        assertTrue(NoteFormatConverter.toMarkdown("", text).contains(text))
    }

    @Test
    fun emptyNoteExportsAsEmptyText() {
        assertEquals("", NoteFormatConverter.toPlainText("", ""))
        assertEquals("", NoteFormatConverter.toMarkdown("", ""))
        assertEquals("Only title\n", NoteFormatConverter.toPlainText("Only title", ""))
    }

    @Test
    fun previewIsOneShortPlainLine() {
        assertEquals("Title  Some text here", NoteFormatConverter.preview("# Title\n\nSome **text** here"))
        val long = NoteFormatConverter.preview("x".repeat(500), maxLength = 20)
        assertEquals(21, long.length) // 20 chars plus the ellipsis
        assertTrue(long.endsWith("…"))
    }
}
