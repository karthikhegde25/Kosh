package com.kosh.android.notes

import com.kosh.android.notes.format.FormatOp
import com.kosh.android.notes.format.NoteFormatting
import com.kosh.android.notes.format.TextState
import com.kosh.android.notes.format.UndoStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFormattingTest {

    private fun apply(op: FormatOp, text: String, a: Int, b: Int = a) = NoteFormatting.apply(op, TextState(text, a, b))

    @Test
    fun boldWrapsSelection() {
        assertEquals(TextState("**hello** world", 2, 7), apply(FormatOp.BOLD, "hello world", 0, 5))
    }

    @Test
    fun boldTogglesOffWhenMarkersSurroundSelection() {
        assertEquals(TextState("hello world", 0, 5), apply(FormatOp.BOLD, "**hello** world", 2, 7))
    }

    @Test
    fun boldAtCaretInsertsEmptyPair() {
        assertEquals(TextState("a****b", 3, 3), apply(FormatOp.BOLD, "ab", 1))
    }

    @Test
    fun italicInsideBoldAddsAnotherLayer() {
        assertEquals(TextState("***bold***", 3, 7), apply(FormatOp.ITALIC, "**bold**", 2, 6))
    }

    @Test
    fun italicTogglesOff() {
        assertEquals(TextState("it", 0, 2), apply(FormatOp.ITALIC, "*it*", 1, 3))
    }

    @Test
    fun strikethroughWraps() {
        assertEquals(TextState("~~x~~", 2, 3), apply(FormatOp.STRIKE, "x", 0, 1))
    }

    @Test
    fun bulletAddedAndRemoved() {
        val added = apply(FormatOp.BULLET, "item", 2)
        assertEquals("- item", added.text)
        assertEquals(4, added.selStart)
        val removed = NoteFormatting.apply(FormatOp.BULLET, added)
        assertEquals("item", removed.text)
        assertEquals(2, removed.selStart)
    }

    @Test
    fun numberedListNumbersEachSelectedLine() {
        assertEquals("1. a\n2. b\n3. c", apply(FormatOp.NUMBERED, "a\nb\nc", 0, 5).text)
    }

    @Test
    fun checklistKeepsExistingCheckedState() {
        assertEquals("- [x] a\n- [ ] b", apply(FormatOp.CHECKLIST, "- [x] a\nb", 0, 9).text)
    }

    @Test
    fun switchingListTypeReplacesThePrefix() {
        assertEquals("1. a", apply(FormatOp.NUMBERED, "- a", 3).text)
    }

    @Test
    fun blankLinesInsideASelectionAreSkipped() {
        assertEquals("- a\n\n- b", apply(FormatOp.BULLET, "a\n\nb", 0, 4).text)
    }

    @Test
    fun selectionEndingAtNextLineStartDoesNotTouchThatLine() {
        assertEquals("- a\nb", apply(FormatOp.BULLET, "a\nb", 0, 2).text)
    }

    @Test
    fun headingCyclesThroughLevelsAndBack() {
        var s = TextState("Title", 0, 0)
        val seen = mutableListOf<String>()
        repeat(4) {
            s = NoteFormatting.apply(FormatOp.HEADING, s)
            seen += s.text
        }
        assertEquals(listOf("# Title", "## Title", "### Title", "Title"), seen)
    }

    @Test
    fun indentAndOutdent() {
        val indented = apply(FormatOp.INDENT, "- a", 0)
        assertEquals("  - a", indented.text)
        assertEquals("- a", NoteFormatting.apply(FormatOp.OUTDENT, indented).text)
        assertEquals("a\n  b", apply(FormatOp.INDENT, "a\nb", 2).text)
    }

    // ---- Enter key in lists

    private fun enter(old: String, new: String, caret: Int) =
        NoteFormatting.continueList(TextState(old, old.length, old.length), TextState(new, caret, caret))

    @Test
    fun enterContinuesBulletNumberedAndChecklist() {
        assertEquals(TextState("- a\n- ", 6, 6), enter("- a", "- a\n", 4))
        assertEquals(TextState("1. a\n2. ", 8, 8), enter("1. a", "1. a\n", 5))
        assertEquals(TextState("- [x] a\n- [ ] ", 14, 14), enter("- [x] a", "- [x] a\n", 8))
        assertEquals(TextState("  - a\n  - ", 10, 10), enter("  - a", "  - a\n", 6))
    }

    @Test
    fun enterOnEmptyItemEndsTheList() {
        assertEquals(TextState("- a\n\n", 4, 4), enter("- a\n- ", "- a\n- \n", 7))
    }

    @Test
    fun enterOnPlainTextOrPasteIsLeftAlone() {
        assertNull(enter("hello", "hello\n", 6))
        assertNull(NoteFormatting.continueList(TextState("- a", 3, 3), TextState("- a\nxyz\n", 8, 8)))
    }
}

class UndoStackTest {
    private val s0 = TextState("", 0, 0)
    private val s1 = TextState("a", 1, 1)
    private val s2 = TextState("ab", 2, 2)

    @Test
    fun undoAndRedoWalkThroughHistory() {
        val stack = UndoStack()
        stack.record(s0, now = 0, force = false)
        assertTrue(stack.canUndo)
        assertEquals(s0, stack.undo(s1))
        assertTrue(stack.canRedo)
        assertEquals(s1, stack.redo(s0))
        assertFalse(stack.canRedo)
    }

    @Test
    fun quickTypingIsGroupedIntoOneStep() {
        val stack = UndoStack(coalesceMs = 1000)
        stack.record(s0, now = 0, force = false)
        stack.record(s1, now = 500, force = false) // within the window: no new step
        assertEquals(s0, stack.undo(s2))
        assertNull(stack.undo(s0))
    }

    @Test
    fun pausingStartsANewStepAndForceAlwaysDoes() {
        val stack = UndoStack(coalesceMs = 1000)
        stack.record(s0, now = 0, force = false)
        stack.record(s1, now = 2000, force = false)
        assertEquals(s1, stack.undo(s2))
        assertEquals(s0, stack.undo(s1))

        val forced = UndoStack()
        forced.record(s0, now = 0, force = false)
        forced.record(s1, now = 10, force = true)
        assertEquals(s1, forced.undo(s2))
    }

    @Test
    fun newEditsClearRedo() {
        val stack = UndoStack()
        stack.record(s0, 0, false)
        stack.undo(s1)
        assertTrue(stack.canRedo)
        stack.record(s0, 5000, false)
        assertFalse(stack.canRedo)
    }

    @Test
    fun historyIsBounded() {
        val stack = UndoStack(limit = 3)
        repeat(10) { stack.record(TextState("$it", 0, 0), now = it * 5000L, force = false) }
        var count = 0
        var current = s0
        while (true) { current = stack.undo(current) ?: break; count++ }
        assertEquals(3, count)
    }
}
