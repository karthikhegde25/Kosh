package com.kosh.android.notes.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 2 adds folders. Add a Migration (never destructive fallback) for every future schema change. */
@Database(entities = [Note::class, NoteFolder::class], version = 3, exportSchema = true)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}

/** Adds the folders table and the folder a note is filed in. Existing notes stay in the main list. */
val NOTES_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("ALTER TABLE `notes` ADD COLUMN `folderId` TEXT")
    }
}

/** Lets a folder have a colour. */
val NOTES_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `note_folders` ADD COLUMN `color` INTEGER")
    }
}
