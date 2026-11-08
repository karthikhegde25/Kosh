package com.kosh.android.notes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class NoteDao {

    @Query("SELECT * FROM notes WHERE trashedAt IS NULL AND isArchived = 0")
    abstract fun observeActive(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE trashedAt IS NULL AND isArchived = 1")
    abstract fun observeArchived(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE trashedAt IS NOT NULL")
    abstract fun observeTrashed(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    abstract suspend fun get(id: String): Note?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(note: Note): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(note: Note)

    @Query("UPDATE notes SET title = :title, content = :content, updatedAt = :now WHERE id = :id")
    abstract suspend fun updateContent(id: String, title: String, content: String, now: Long): Int

    /**
     * Creates the row on the first save, then updates only title/content/updatedAt, so autosave can never
     * overwrite pin, archive or trash changes made elsewhere. createdAt is never touched after creation.
     */
    @Transaction
    open suspend fun saveContent(id: String, title: String, content: String, now: Long) {
        insertIfAbsent(Note(id = id, title = title, content = content, createdAt = now, updatedAt = now))
        updateContent(id, title, content, now)
    }

    @Query("UPDATE notes SET isPinned = :pinned WHERE id = :id")
    abstract suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE notes SET isArchived = :archived WHERE id = :id")
    abstract suspend fun setArchived(id: String, archived: Boolean)

    @Query("UPDATE notes SET trashedAt = :trashedAt WHERE id = :id")
    abstract suspend fun setTrashedAt(id: String, trashedAt: Long?)

    @Query("DELETE FROM notes WHERE id = :id")
    abstract suspend fun delete(id: String)

    // ---- several notes at once ----

    @Query("UPDATE notes SET isArchived = :archived WHERE id IN (:ids)")
    abstract suspend fun setArchivedMany(ids: List<String>, archived: Boolean)

    @Query("UPDATE notes SET trashedAt = :trashedAt WHERE id IN (:ids)")
    abstract suspend fun setTrashedAtMany(ids: List<String>, trashedAt: Long?)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    abstract suspend fun deleteMany(ids: List<String>)

    @Query("UPDATE notes SET folderId = :folderId WHERE id IN (:ids)")
    abstract suspend fun setFolderMany(ids: List<String>, folderId: String?)

    // ---- folders ----

    @Query("SELECT * FROM note_folders")
    abstract fun observeFolders(): Flow<List<NoteFolder>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertFolder(folder: NoteFolder)

    @Query("UPDATE note_folders SET name = :name, updatedAt = :now WHERE id = :id")
    abstract suspend fun renameFolder(id: String, name: String, now: Long)

    @Query("UPDATE notes SET folderId = NULL WHERE folderId = :id")
    abstract suspend fun clearFolder(id: String)

    @Query("DELETE FROM note_folders WHERE id = :id")
    abstract suspend fun deleteFolderRow(id: String)

    @Query("UPDATE note_folders SET color = :color, updatedAt = :now WHERE id = :id")
    abstract suspend fun setFolderColor(id: String, color: Int?, now: Long)

    @Query("UPDATE notes SET trashedAt = :now, folderId = NULL WHERE folderId = :id AND trashedAt IS NULL")
    abstract suspend fun trashFolderNotes(id: String, now: Long)

    /** Removes the folder and sends every note in it to Trash (where it can still be restored). */
    @Transaction
    open suspend fun deleteFolderWithNotes(id: String, now: Long) {
        trashFolderNotes(id, now)
        clearFolder(id)
        deleteFolderRow(id)
    }

    /** Deletes only the folder; its notes go back to the main list. */
    @Transaction
    open suspend fun deleteFolder(id: String) {
        clearFolder(id)
        deleteFolderRow(id)
    }
}
