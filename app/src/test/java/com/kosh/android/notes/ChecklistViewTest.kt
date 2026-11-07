package com.kosh.android.notes

import com.kosh.android.notes.ui.BOX_CHECKED
import com.kosh.android.notes.ui.BOX_EMPTY
import com.kosh.android.notes.ui.buildChecklistView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistViewTest {

    private val sample = "Todo\n- [ ] milk\n  - [x] eggs\nend"

    @Test
    fun markersBecomeBoxes() {
        val v = buildChecklistView(sample)
        assertEquals("Todo\n$BOX_EMPTY milk\n  $BOX_CHECKED eggs\nend", v.text)
    }

    @Test
    fun boxesRecordWhichAreTicked() {
        val v = buildChecklistView(sample)
        assertEquals(listOf(false, true), v.boxes.map { it.second })
        v.boxes.forEach { (index, ticked) -> assertEquals(if (ticked) BOX_CHECKED else BOX_EMPTY, v.text[index]) }
    }

    @Test
    fun onlyTickedItemTextIsStruckThrough() {
        val v = buildChecklistView(sample)
        assertEquals(1, v.tickedText.size)
        assertEquals("eggs", v.text.substring(v.tickedText[0].first, v.tickedText[0].last + 1))
    }

    @Test
    fun lineBreaksAreNeverAddedOrRemoved() {
        val v = buildChecklistView(sample)
        assertEquals(sample.count { it == '\n' }, v.text.count { it == '\n' })
    }

    @Test
    fun offsetMappingsStayInRangeAndNeverGoBackwards() {
        for (src in listOf(sample, "- [ ] a\n", "", "- [x] ", "- [ ] \n- [ ] b", "plain\n\n- [ ] z")) {
            val v = buildChecklistView(src)
            assertEquals(src.length + 1, v.originalToTransformed.size)
            assertEquals(v.text.length + 1, v.transformedToOriginal.size)
            assertEquals(v.text.length, v.originalToTransformed[src.length])
            assertEquals(src.length, v.transformedToOriginal[v.text.length])
            v.originalToTransformed.forEach { assertTrue(it in 0..v.text.length) }
            v.transformedToOriginal.forEach { assertTrue(it in 0..src.length) }
            for (i in 1 until v.originalToTransformed.size) assertTrue(v.originalToTransformed[i] >= v.originalToTransformed[i - 1])
            for (i in 1 until v.transformedToOriginal.size) assertTrue(v.transformedToOriginal[i] >= v.transformedToOriginal[i - 1])
        }
    }

    @Test
    fun normalTextMapsOneToOne() {
        val v = buildChecklistView(sample)
        // "Todo\n" is before any marker: identical offsets.
        for (i in 0..5) {
            assertEquals(i, v.originalToTransformed[i])
            assertEquals(i, v.transformedToOriginal[i])
        }
        // A position after the marker, inside "milk", round-trips.
        val milk = sample.indexOf("milk") + 2
        assertEquals(milk, v.transformedToOriginal[v.originalToTransformed[milk]])
    }

    @Test
    fun theCursorCannotSitInsideTheHiddenMarker() {
        val v = buildChecklistView("- [ ] milk")
        // Offsets 1..5 are inside "- [ ] ": they all show as "just before the box".
        for (i in 0..5) assertEquals(0, v.originalToTransformed[i])
        assertEquals(2, v.originalToTransformed[6]) // right after the marker = after box and space
        assertEquals(0, v.transformedToOriginal[0])
        assertEquals(6, v.transformedToOriginal[1])
        assertEquals(6, v.transformedToOriginal[2])
    }

    @Test
    fun linesThatAreNotCompleteMarkersAreLeftAlone() {
        for (src in listOf("- [ ]", "-[ ] x", "- [] x", "* item", "- [y] x", "text - [ ] x")) {
            assertEquals(src, buildChecklistView(src).text)
        }
    }

    @Test
    fun otherBulletCharactersAreAlsoChecklists() {
        assertEquals("$BOX_EMPTY a\n$BOX_CHECKED b", buildChecklistView("* [ ] a\n+ [X] b").text)
    }
}
