package com.kosh.android.notes

import com.kosh.android.notes.data.Note
import com.kosh.android.notes.data.NoteSort
import com.kosh.android.notes.data.NotesPrefs
import com.kosh.android.notes.ui.filterAndSort
import com.kosh.android.util.suggestedExportName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesListTest {
    private fun n(id: String, title: String, content: String = "", created: Long = 0, updated: Long = 0) =
        Note(id = id, title = title, content = content, createdAt = created, updatedAt = updated)

    private val notes = listOf(
        n("1", "Groceries", "milk, eggs", created = 1, updated = 30),
        n("2", "work plan", "Quarterly GOALS", created = 3, updated = 10),
        n("3", "Zebra", "", created = 2, updated = 20),
    )

    @Test
    fun defaultSortIsLastModifiedNewestFirst() {
        assertEquals(listOf("1", "3", "2"), filterAndSort(notes, "", NotesPrefs()).map { it.id })
    }

    @Test
    fun ascendingFlipsTheOrder() {
        assertEquals(listOf("2", "3", "1"), filterAndSort(notes, "", NotesPrefs(ascending = true)).map { it.id })
    }

    @Test
    fun sortByCreatedAndByTitle() {
        assertEquals(listOf("2", "3", "1"), filterAndSort(notes, "", NotesPrefs(sort = NoteSort.CREATED)).map { it.id })
        assertEquals(
            listOf("1", "2", "3"),
            filterAndSort(notes, "", NotesPrefs(sort = NoteSort.TITLE, ascending = true)).map { it.id },
        )
    }

    @Test
    fun searchMatchesTitleAndBodyIgnoringCase() {
        assertEquals(listOf("2"), filterAndSort(notes, "WORK", NotesPrefs()).map { it.id })
        assertEquals(listOf("2"), filterAndSort(notes, "goals", NotesPrefs()).map { it.id })
        assertEquals(listOf("1"), filterAndSort(notes, " eggs ", NotesPrefs()).map { it.id })
    }

    @Test
    fun noMatchGivesAnEmptyList() {
        assertTrue(filterAndSort(notes, "nothing like this", NotesPrefs()).isEmpty())
    }

    @Test
    fun exportNamesAreSanitised() {
        assertEquals("My Plan2026.pdf", suggestedExportName("My: Plan/2026?", "pdf"))
        assertEquals("Note.md", suggestedExportName("///", "md"))
        assertEquals("Note.txt", suggestedExportName("   ", "txt"))
        assertEquals("hidden.pdf", suggestedExportName("..hidden..", "pdf"))
        assertEquals("a bc.pdf", suggestedExportName("a   b\tc", "pdf"))
        assertEquals("Rapport été.txt", suggestedExportName("Rapport été", "txt"))
        assertEquals(104, suggestedExportName("a".repeat(300), "txt").length)
    }
}
