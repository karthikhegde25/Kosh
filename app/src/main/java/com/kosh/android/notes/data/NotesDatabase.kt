package com.kosh.android.notes.data

import androidx.room.Database
import androidx.room.RoomDatabase

/** Version 1. Add a Migration (never destructive fallback) for every future schema change. */
@Database(entities = [Note::class], version = 1, exportSchema = true)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}
