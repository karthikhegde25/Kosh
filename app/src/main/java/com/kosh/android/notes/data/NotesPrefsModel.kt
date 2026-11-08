package com.kosh.android.notes.data

enum class NotesView { ACTIVE, ARCHIVE, TRASH }

enum class NoteSort { UPDATED, CREATED, TITLE }

/** What the Notes tab lists: everything, every note (also those inside folders), or only the folders. */
enum class NotesShow { ALL, NOTES, FOLDERS }

data class NotesPrefs(
    val sort: NoteSort = NoteSort.UPDATED,
    val ascending: Boolean = false,
    val grid: Boolean = false,
    val show: NotesShow = NotesShow.ALL,
)
