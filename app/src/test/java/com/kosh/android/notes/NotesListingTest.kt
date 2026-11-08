package com.kosh.android.notes

import com.kosh.android.notes.data.Note
import com.kosh.android.notes.data.NoteFolder
import com.kosh.android.notes.data.NoteSort
import com.kosh.android.notes.data.NotesPrefs
import com.kosh.android.notes.data.NotesShow
import com.kosh.android.notes.data.NotesView
import com.kosh.android.notes.ui.buildListing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesListingTest {

    private fun note(id: String, folder: String? = null, pinned: Boolean = false, title: String = id, updated: Long = 0, content: String = "") =
        Note(id = id, title = title, content = content, createdAt = 0, updatedAt = updated, isPinned = pinned, folderId = folder)

    private fun folder(id: String, name: String = id) = NoteFolder(id, name, 0, 0)

    private val folders = listOf(folder("f1", "Work"), folder("f2", "Home"))
    private val notes = listOf(
        note("a", updated = 3),
        note("b", folder = "f1", updated = 2),
        note("c", folder = "f1", updated = 1),
        note("d", folder = "gone", updated = 4),
        note("p", pinned = true, updated = 5),
    )

    private fun ids(list: List<Note>) = list.map { it.id }

    @Test fun mainListShowsFoldersThenNotesNotInAFolder() {
        val l = buildListing(NotesView.ACTIVE, notes, folders, null, "", NotesPrefs())
        assertEquals(listOf("f1", "f2").toSet(), l.folders.map { it.folder.id }.toSet())
        assertEquals(listOf("p"), ids(l.pinned))
        // "d" is in a folder that no longer exists, so it is in the main list.
        assertEquals(listOf("d", "a"), ids(l.notes))
    }

    @Test fun folderCountsOnlyCountItsNotes() {
        val l = buildListing(NotesView.ACTIVE, notes, folders, null, "", NotesPrefs())
        assertEquals(2, l.folders.first { it.folder.id == "f1" }.noteCount)
        assertEquals(0, l.folders.first { it.folder.id == "f2" }.noteCount)
    }

    @Test fun insideAFolderOnlyItsNotesShow() {
        val l = buildListing(NotesView.ACTIVE, notes, folders, "f1", "", NotesPrefs())
        assertTrue(l.folders.isEmpty())
        assertEquals(listOf("b", "c"), ids(l.notes))
    }

    @Test fun onlyNotesShowsEveryNoteAndNoFolders() {
        val l = buildListing(NotesView.ACTIVE, notes, folders, null, "", NotesPrefs(show = NotesShow.NOTES))
        assertTrue(l.folders.isEmpty())
        assertEquals(notes.map { it.id }.toSet(), (ids(l.pinned) + ids(l.notes)).toSet())
    }

    @Test fun onlyFoldersShowsNoNotes() {
        val l = buildListing(NotesView.ACTIVE, notes, folders, null, "", NotesPrefs(show = NotesShow.FOLDERS))
        assertEquals(2, l.folders.size)
        assertTrue(l.pinned.isEmpty() && l.notes.isEmpty())
    }

    @Test fun onlyFoldersStillLetsYouLookInsideOne() {
        val l = buildListing(NotesView.ACTIVE, notes, folders, "f1", "", NotesPrefs(show = NotesShow.FOLDERS))
        assertEquals(listOf("b", "c"), ids(l.notes))
    }

    @Test fun searchLooksThroughAllNotesAndFolderNames() {
        val l = buildListing(NotesView.ACTIVE, notes, folders, null, "work", NotesPrefs())
        assertEquals(listOf("f1"), l.folders.map { it.folder.id })
        val inFolder = buildListing(NotesView.ACTIVE, listOf(note("x", folder = "f1", content = "buy milk")), folders, null, "milk", NotesPrefs())
        assertEquals(listOf("x"), ids(inFolder.notes))
    }

    @Test fun foldersSortByName() {
        val l = buildListing(NotesView.ACTIVE, emptyList(), folders, null, "", NotesPrefs(sort = NoteSort.TITLE, ascending = true))
        assertEquals(listOf("Home", "Work"), l.folders.map { it.folder.name })
    }

    @Test fun archiveAndTrashListEverythingWithoutFolders() {
        for (view in listOf(NotesView.ARCHIVE, NotesView.TRASH)) {
            val l = buildListing(view, notes, folders, "f1", "", NotesPrefs(show = NotesShow.FOLDERS))
            assertTrue(l.folders.isEmpty() && l.pinned.isEmpty())
            assertEquals(notes.size, l.notes.size)
        }
    }
}
