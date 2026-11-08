package com.kosh.android.data.repository

import android.content.Context
import android.net.Uri
import com.kosh.android.data.model.CropCorners
import com.kosh.android.data.model.Document
import com.kosh.android.data.model.FilterMode
import com.kosh.android.data.model.ScannedPage
import com.kosh.android.imaging.ImageImporter
import com.kosh.android.imaging.ImportResult
import com.kosh.android.util.clampTitle
import com.kosh.android.util.defaultTitle
import com.kosh.android.util.moveItem
import com.kosh.android.util.normalizeRotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ImportSummary(val added: Int, val failed: Int) {
    val hasFailures get() = failed > 0
}

/**
 * Holds the current in-progress document. Page originals live in app-private storage
 * (`files/drafts/<documentId>/`); edits (crop, rotation, filter) are stored as parameters, never baked into the file.
 * Draft state is in memory: it survives navigation and rotation but not the process being killed.
 */
class DraftRepository(private val context: Context) {

    private val root = File(context.filesDir, "drafts")
    private val importer = ImageImporter(context.contentResolver)
    private val mutex = Mutex()

    private val _draft = MutableStateFlow<Document?>(null)
    val draft: StateFlow<Document?> = _draft.asStateFlow()

    private fun newDocument(now: Long) = Document(
        id = UUID.randomUUID().toString(),
        title = defaultTitle(),
        createdAt = now,
        updatedAt = now,
    )

    suspend fun importImages(uris: List<Uri>, defaultFilter: FilterMode): ImportSummary = mutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val doc = _draft.value ?: newDocument(now)
            val added = mutableListOf<ScannedPage>()
            var failed = 0
            for (uri in uris) {
                val pageId = UUID.randomUUID().toString()
                val file = File(root, "${doc.id}/$pageId.img")
                when (importer.import(uri, file)) {
                    is ImportResult.Success -> added += ScannedPage(
                        id = pageId,
                        documentId = doc.id,
                        originalPath = file.absolutePath,
                        filterMode = defaultFilter,
                        createdAt = now,
                    )
                    is ImportResult.Failure -> failed++
                }
            }
            if (added.isNotEmpty()) {
                _draft.value = doc.copy(pages = doc.pages + added, updatedAt = now)
            }
            ImportSummary(added = added.size, failed = failed)
        }
    }

    /** Where the camera should write the next capture. Created inside the draft's private folder. */
    suspend fun newCaptureFile(): File = mutex.withLock {
        withContext(Dispatchers.IO) {
            val doc = _draft.value ?: newDocument(System.currentTimeMillis()).also { _draft.value = it }
            File(root, "${doc.id}/${UUID.randomUUID()}.img").also { it.parentFile?.mkdirs() }
        }
    }

    /** Registers a file the camera wrote (see [newCaptureFile]) as a new page. Returns the page, or null if unreadable. */
    suspend fun addCapturedFile(file: File, filter: FilterMode): ScannedPage? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val doc = _draft.value ?: return@withContext null
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                file.delete()
                return@withContext null
            }
            val now = System.currentTimeMillis()
            val page = ScannedPage(
                id = file.nameWithoutExtension,
                documentId = doc.id,
                originalPath = file.absolutePath,
                filterMode = filter,
                createdAt = now,
            )
            _draft.value = doc.copy(pages = doc.pages + page, updatedAt = now)
            page
        }
    }

    fun page(id: String): ScannedPage? = _draft.value?.pages?.firstOrNull { it.id == id }

    fun renameDraft(title: String) {
        update { it.copy(title = clampTitle(title)) }
    }

    fun rotatePage(id: String, deltaDegrees: Int) = updatePage(id) {
        it.copy(rotationDegrees = normalizeRotation(it.rotationDegrees + deltaDegrees))
    }

    fun setFilter(id: String, mode: FilterMode) = updatePage(id) { it.copy(filterMode = mode) }

    fun setFilterAll(mode: FilterMode) = update { doc -> doc.copy(pages = doc.pages.map { it.copy(filterMode = mode) }) }
    fun setCrop(id: String, corners: CropCorners?) = updatePage(id) { it.copy(cropCorners = corners) }

    fun movePage(id: String, delta: Int) = update { doc ->
        val index = doc.pages.indexOfFirst { it.id == id }
        doc.copy(pages = moveItem(doc.pages, index, delta))
    }

    suspend fun deletePage(id: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val page = page(id) ?: return@withContext
            update { doc -> doc.copy(pages = doc.pages.filterNot { it.id == id }) }
            File(page.originalPath).delete()
        }
    }

    /** Copies the page (and its original file) right after the source page. */
    suspend fun duplicatePage(id: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val source = page(id) ?: return@withContext
            val newId = UUID.randomUUID().toString()
            val copyFile = File(File(source.originalPath).parentFile, "$newId.img")
            File(source.originalPath).copyTo(copyFile, overwrite = false)
            val copy = source.copy(id = newId, originalPath = copyFile.absolutePath, createdAt = System.currentTimeMillis())
            update { doc ->
                val index = doc.pages.indexOfFirst { it.id == id }
                doc.copy(pages = doc.pages.toMutableList().apply { add(index + 1, copy) })
            }
        }
    }

    /** Removes the draft and its files after a PDF was exported, or when the user abandons it. */
    suspend fun discardDraft() = mutex.withLock {
        withContext(Dispatchers.IO) {
            _draft.value?.let { File(root, it.id).deleteRecursively() }
            _draft.value = null
        }
    }

    /** Removes every temporary draft. Exported PDFs live elsewhere and are never touched. */
    suspend fun clearAllDrafts() = mutex.withLock {
        withContext(Dispatchers.IO) {
            root.deleteRecursively()
            _draft.value = null
        }
    }

    private fun updatePage(id: String, change: (ScannedPage) -> ScannedPage) = update { doc ->
        doc.copy(pages = doc.pages.map { if (it.id == id) change(it) else it })
    }

    private fun update(change: (Document) -> Document) {
        _draft.update { current -> current?.let { change(it).copy(updatedAt = System.currentTimeMillis()) } }
    }
}
