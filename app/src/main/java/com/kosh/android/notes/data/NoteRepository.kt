package com.kosh.android.notes.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

enum class NotesView { ACTIVE, ARCHIVE, TRASH }

class NoteRepository(private val dao: NoteDao) {

    private class SaveRequest(
        val id: String,
        val title: String,
        val content: String,
        val timestamp: Long,
        val result: CompletableDeferred<Result<Unit>>,
    )

    // Edits are written one at a time, in the order they were requested, by a single consumer. That way an
    // older snapshot can never land after a newer one, and writes still finish if the editor screen is gone.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saves = Channel<SaveRequest>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (request in saves) {
                val outcome = runCatching { dao.saveContent(request.id, request.title, request.content, request.timestamp) }
                request.result.complete(outcome)
            }
        }
    }

    /** Queues a save of the given snapshot. The returned deferred completes once it has been written. */
    fun enqueueSave(id: String, title: String, content: String): Deferred<Result<Unit>> {
        val result = CompletableDeferred<Result<Unit>>()
        saves.trySend(SaveRequest(id, title, content, System.currentTimeMillis(), result))
        return result
    }

    fun observe(view: NotesView): Flow<List<Note>> = when (view) {
        NotesView.ACTIVE -> dao.observeActive()
        NotesView.ARCHIVE -> dao.observeArchived()
        NotesView.TRASH -> dao.observeTrashed()
    }

    suspend fun get(id: String): Note? = dao.get(id)

    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, pinned)

    suspend fun setArchived(id: String, archived: Boolean) = dao.setArchived(id, archived)

    suspend fun moveToTrash(id: String) = dao.setTrashedAt(id, System.currentTimeMillis())

    suspend fun restore(id: String) = dao.setTrashedAt(id, null)

    suspend fun deletePermanently(id: String) = dao.delete(id)

    /** Copies a note (title gets [copySuffix]); the copy is unpinned, unarchived and has fresh timestamps. */
    suspend fun duplicate(id: String, copySuffix: String): String? {
        val original = dao.get(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            title = if (original.title.isBlank()) "" else original.title + copySuffix,
            createdAt = now,
            updatedAt = now,
            isPinned = false,
            isArchived = false,
            trashedAt = null,
        )
        dao.insert(copy)
        return copy.id
    }
}
