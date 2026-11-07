package com.kosh.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Bookmarks, annotations and scan records. Separate from the Notes database so each can evolve on its own.
 * Version 2 (1 -> 2 added highlights, see MIGRATION_1_2). Add a Migration (never a destructive fallback) for every future schema change.
 */
@Database(
    entities = [Bookmark::class, PdfAnnotation::class, ScanRecord::class, HighlightEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun scanRecordDao(): ScanRecordDao
    abstract fun highlightDao(): HighlightDao
}
