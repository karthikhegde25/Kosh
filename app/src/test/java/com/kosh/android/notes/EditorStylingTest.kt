package com.kosh.android.notes

import com.kosh.android.notes.format.StyleKind
import com.kosh.android.notes.format.StyleRange
import com.kosh.android.notes.format.editorStyles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorStylingTest {
    private fun of(text: String, kind: StyleKind) = editorStyles(text).filter { it.kind == kind }.map { text.substring(it.start, it.end) }

    @Test fun boldItalicStrikeAndCode() {
        val t = "a **bold** b *it* c ~~gone~~ d `code`"
        assertEquals(listOf("bold"), of(t, StyleKind.BOLD))
        assertEquals(listOf("it"), of(t, StyleKind.ITALIC))
        assertEquals(listOf("gone"), of(t, StyleKind.STRIKE))
        assertEquals(listOf("`code`"), of(t, StyleKind.CODE))
    }

    @Test fun markersAreMarked() {
        assertEquals(listOf("**", "**"), of("**x**", StyleKind.MARKER))
    }

    @Test fun boldIsNotAlsoItalic() {
        assertTrue(of("**bold**", StyleKind.ITALIC).isEmpty())
    }

    @Test fun headingsStyleTheWholeLine() {
        val t = "# Title\nbody\n## Sub"
        assertEquals(listOf("# Title"), of(t, StyleKind.HEADING1))
        assertEquals(listOf("## Sub"), of(t, StyleKind.HEADING2))
        assertEquals(listOf("# ", "## "), of(t, StyleKind.MARKER))
    }

    @Test fun starsInsideCodeAreLeftAlone() {
        assertTrue(of("`**x**`", StyleKind.BOLD).isEmpty())
    }

    @Test fun unfinishedMarkersDoNothing() {
        assertTrue(editorStyles("half **open and * lone and ~~x").isEmpty())
        assertTrue(editorStyles("2 * 3 * 4").isEmpty())
    }

    @Test fun stylesNeverCrossLines() {
        assertTrue(editorStyles("**a\nb**").isEmpty())
    }

    @Test fun emptyAndPlainText() {
        assertTrue(editorStyles("").isEmpty())
        assertTrue(editorStyles("just words\nmore words").isEmpty())
    }

    @Test fun offsetsAreAbsoluteAcrossLines() {
        val t = "one\n**two**"
        assertEquals(StyleRange(6, 9, StyleKind.BOLD), editorStyles(t).first { it.kind == StyleKind.BOLD })
    }
}
