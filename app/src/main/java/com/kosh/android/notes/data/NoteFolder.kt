package com.kosh.android.notes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A folder that groups notes in the Notes tab. Folders are one level deep. Timestamps are epoch milliseconds. */
@Entity(tableName = "note_folders")
data class NoteFolder(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** ARGB colour chosen for the folder, or null for the default. */
    val color: Int? = null,
)

/** Colours offered for folders. */
val FOLDER_COLORS: List<Int> = listOf(
    0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(),
    0xFF00ACC1.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFF6D4C41.toInt(),
)
