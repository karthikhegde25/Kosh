package com.kosh.android.notes

import com.kosh.android.notes.tts.buildSpeakable
import com.kosh.android.notes.tts.chunkIndexAt
import com.kosh.android.notes.tts.speechChunks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechPlanTest {

    @Test fun plainTextIsSpokenAsWritten() {
        val s = buildSpeakable("Hello world. Second one.")
        assertEquals("Hello world. Second one.", s.text)
    }

    @Test fun markdownSignsAreLeftOut() {
        val s = buildSpeakable("# Title\n**bold** and *it* and ~~gone~~ and `code`")
        assertEquals("Title\nbold and it and gone and code", s.text)
    }

    @Test fun listAndChecklistMarkersAreLeftOut() {
        val s = buildSpeakable("- apples\n* pears\n- [ ] buy milk\n- [x] done\n1. first\n> quoted")
        assertEquals("apples\npears\nbuy milk\ndone\n1. first\nquoted", s.text)
    }

    @Test fun linksKeepOnlyTheirText() {
        assertEquals("see the site now", buildSpeakable("see [the site](https://x.y/z) now").text)
    }

    @Test fun pictureLinesAreSkipped() {
        val s = buildSpeakable("before\n![image](kosh-image:a.jpg)\nafter")
        assertEquals("before\n\nafter", s.text)
    }

    @Test fun spokenPositionsMapBackToTheNote() {
        val note = "# Title\nsome **bold** text"
        val s = buildSpeakable(note)
        val at = s.text.indexOf("bold")
        val range = s.noteRange(at, at + 4)
        assertEquals("bold", note.substring(range.first, range.last + 1))
        val title = s.noteRange(0, 5)
        assertEquals("Title", note.substring(title.first, title.last + 1))
        assertEquals(at, s.positionOfNote(note.indexOf("bold")))
    }

    @Test fun chunksEndAtSentences() {
        val text = "One short sentence. Another one here! And a third?"
        val chunks = speechChunks(text, 25)
        assertEquals(listOf("One short sentence.", "Another one here!", "And a third?"), chunks.map { text.substring(it.start, it.end) })
    }

    @Test fun newlinesSeparateChunks() {
        val text = "first line\nsecond line"
        assertEquals(listOf("first line", "second line"), speechChunks(text).map { text.substring(it.start, it.end) })
    }

    @Test fun longTextWithoutPunctuationIsCutAtSpaces() {
        val text = "word ".repeat(100).trim()
        val chunks = speechChunks(text, 50)
        assertTrue(chunks.all { it.end - it.start <= 50 })
        assertEquals(text, chunks.joinToString(" ") { text.substring(it.start, it.end) })
    }

    @Test fun veryLongWordIsCutAtTheLimit() {
        val chunks = speechChunks("x".repeat(130), 50)
        assertEquals(listOf(50, 50, 30), chunks.map { it.end - it.start })
    }

    @Test fun emptyOrBlankTextHasNoChunks() {
        assertTrue(speechChunks("  \n ").isEmpty())
        assertTrue(buildSpeakable("").text.isEmpty())
    }

    @Test fun chunkLookup() {
        val chunks = speechChunks("aaa. bbb. ccc.", 6)
        assertEquals(0, chunkIndexAt(chunks, 0))
        assertEquals(1, chunkIndexAt(chunks, 6))
        assertEquals(chunks.lastIndex, chunkIndexAt(chunks, 999))
    }
}
