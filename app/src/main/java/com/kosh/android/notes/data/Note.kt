package com.kosh.android.notes.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A note. [content] is Markdown source ([contentFormat] marks the format for future migrations).
 * Timestamps are epoch milliseconds (UTC). A note is in Trash when [trashedAt] is set.
 */
@Entity(tableName = "notes", indices = [Index("trashedAt"), Index("isArchived")])
data class Note(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val contentFormat: String = FORMAT_MARKDOWN,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val trashedAt: Long? = null,
    /** The folder this note is filed in; null (or a folder that no longer exists) means the main list. */
    val folderId: String? = null,
) {
    companion object {
        const val FORMAT_MARKDOWN = "MARKDOWN"
    }
}
