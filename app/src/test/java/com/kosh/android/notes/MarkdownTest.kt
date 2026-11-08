package com.kosh.android.notes

import com.kosh.android.notes.format.MdBlank
import com.kosh.android.notes.format.MdBullet
import com.kosh.android.notes.format.MdChecklist
import com.kosh.android.notes.format.MdHeading
import com.kosh.android.notes.format.MdNumbered
import com.kosh.android.notes.format.MdParagraph
import com.kosh.android.notes.format.Span
import com.kosh.android.notes.format.parseInline
import com.kosh.android.notes.format.parseMarkdown
import com.kosh.android.notes.format.toggleChecklistLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    @Test
    fun inlineStylesAreParsed() {
        assertEquals(
            listOf(
                Span("a "), Span("b", bold = true), Span(" "), Span("c", italic = true), Span(" "),
                Span("d", strike = true), Span(" "), Span("e", code = true),
            ),
            parseInline("a **b** *c* ~~d~~ `e`"),
        )
    }

    @Test
    fun unmatchedMarkersStayLiteral() {
        assertEquals(listOf(Span("2 * 3 and **x")), parseInline("2 * 3 and **x"))
    }

    @Test
    fun escapedMarkersStayLiteral() {
        assertEquals(listOf(Span("*not italic*")), parseInline("\\*not italic\\*"))
    }

    @Test
    fun underscoresInsideWordsAreNotItalic() {
        assertEquals(listOf(Span("snake_case_name")), parseInline("snake_case_name"))
    }

    @Test
    fun blocksAreRecognised() {
        val blocks = parseMarkdown("# Title\nText\n- a\n  - b\n1. one\n- [ ] todo\n- [x] done\n\nend")
        assertTrue(blocks[0] is MdHeading && (blocks[0] as MdHeading).level == 1)
        assertTrue(blocks[1] is MdParagraph)
        assertEquals(0, (blocks[2] as MdBullet).level)
        assertEquals(1, (blocks[3] as MdBullet).level)
        assertEquals(1, (blocks[4] as MdNumbered).number)
        assertEquals(false, (blocks[5] as MdChecklist).checked)
        assertEquals(true, (blocks[6] as MdChecklist).checked)
        assertTrue(blocks[7] is MdBlank)
        assertTrue(blocks[8] is MdParagraph)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8), blocks.map { it.line })
    }

    @Test
    fun boldAtLineStartIsNotABullet() {
        val block = parseMarkdown("**bold** text").single()
        assertTrue(block is MdParagraph)
    }

    @Test
    fun checklistItemsCanBeToggledByLine() {
        val text = "- [ ] a\n- [x] b\nplain"
        assertEquals("- [x] a\n- [x] b\nplain", toggleChecklistLine(text, 0))
        assertEquals("- [ ] a\n- [ ] b\nplain", toggleChecklistLine(text, 1))
        assertEquals(text, toggleChecklistLine(text, 2)) // not a checklist line
        assertEquals(text, toggleChecklistLine(text, 99)) // out of range
    }
}
