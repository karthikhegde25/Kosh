package com.kosh.android.notes.ui

import com.kosh.android.notes.data.Note
import com.kosh.android.notes.data.NoteFolder
import com.kosh.android.notes.data.NoteSort
import com.kosh.android.notes.data.NotesPrefs
import com.kosh.android.notes.data.NotesShow
import com.kosh.android.notes.data.NotesView

/*
 * What the Notes tab lists for the current view, folder, search and sort settings. No Android classes, so it is tested on a
 * plain JVM.
 */

/** A folder with the number of (active) notes inside it. */
data class FolderItem(val folder: NoteFolder, val noteCount: Int)

data class NotesListing(
    val folders: List<FolderItem>,
    val pinned: List<Note>,
    val notes: List<Note>,
)

/** Filters by query (title and body, case-insensitive) and sorts. */
fun filterAndSort(notes: List<Note>, query: String, prefs: NotesPrefs): List<Note> {
    val q = query.trim()
    val filtered = if (q.isEmpty()) notes else notes.filter {
        it.title.contains(q, ignoreCase = true) || it.content.contains(q, ignoreCase = true)
    }
    val base: Comparator<Note> = when (prefs.sort) {
        NoteSort.UPDATED -> compareBy { it.updatedAt }
        NoteSort.CREATED -> compareBy { it.createdAt }
        NoteSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    }
    return filtered.sortedWith(if (prefs.ascending) base else base.reversed())
}

private fun sortFolders(folders: List<NoteFolder>, prefs: NotesPrefs): List<NoteFolder> {
    val base: Comparator<NoteFolder> = when (prefs.sort) {
        NoteSort.UPDATED -> compareBy { it.updatedAt }
        NoteSort.CREATED -> compareBy { it.createdAt }
        NoteSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
    return folders.sortedWith(if (prefs.ascending) base else base.reversed())
}

/**
 * - Archive and Trash list every note in them (folders play no part there).
 * - Inside a folder ([openFolderId]): that folder's notes.
 * - Notes tab, show "everything": the folders, then the notes that are not in a folder.
 * - Show "only notes": every active note, also those inside folders. Show "only folders": just the folders.
 * - A search looks through every active note and the folder names, whichever folder they are in.
 * A note whose folder no longer exists counts as being in no folder.
 */
fun buildListing(
    view: NotesView,
    notes: List<Note>,
    folders: List<NoteFolder>,
    openFolderId: String?,
    query: String,
    prefs: NotesPrefs,
): NotesListing {
    if (view != NotesView.ACTIVE) return NotesListing(emptyList(), emptyList(), filterAndSort(notes, query, prefs))

    val q = query.trim()
    val folderIds = folders.mapTo(HashSet()) { it.id }
    val counts = notes.filter { it.folderId != null }.groupingBy { it.folderId }.eachCount()

    val atRoot = openFolderId == null
    val showFolders = atRoot && prefs.show != NotesShow.NOTES
    val showNotes = !atRoot || prefs.show != NotesShow.FOLDERS

    val folderItems = if (!showFolders) emptyList() else {
        val matching = if (q.isEmpty()) folders else folders.filter { it.name.contains(q, ignoreCase = true) }
        sortFolders(matching, prefs).map { FolderItem(it, counts[it.id] ?: 0) }
    }

    val pool = when {
        !showNotes -> emptyList()
        !atRoot -> notes.filter { it.folderId == openFolderId }
        prefs.show == NotesShow.NOTES || q.isNotEmpty() -> notes
        else -> notes.filter { it.folderId == null || it.folderId !in folderIds }
    }
    val sorted = filterAndSort(pool, q, prefs)
    val (pinned, others) = sorted.partition { it.isPinned }
    return NotesListing(folderItems, pinned, others)
}
