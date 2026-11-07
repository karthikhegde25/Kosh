package com.kosh.android.data.db

import kotlinx.coroutines.flow.Flow

/** Bookmarks and annotations for the PDF reader. [documentKey] is a stable hash of the document URI. */
class ReaderDataRepository(
    private val bookmarks: BookmarkDao,
    private val annotations: AnnotationDao,
) {
    fun observeBookmarks(key: String): Flow<List<Bookmark>> = bookmarks.observe(key)
    suspend fun addBookmark(b: Bookmark) = bookmarks.insert(b)
    suspend fun deleteBookmark(id: String) = bookmarks.delete(id)

    fun observeAnnotations(key: String): Flow<List<PdfAnnotation>> = annotations.observe(key)
    suspend fun addAnnotation(a: PdfAnnotation) = annotations.insert(a)
    suspend fun deleteAnnotation(id: String) = annotations.delete(id)
}

class ScanRecordRepository(private val dao: ScanRecordDao) {
    val records: Flow<List<ScanRecord>> = dao.observeAll()
    suspend fun add(record: ScanRecord) = dao.insert(record)
    suspend fun rename(id: String, name: String, uri: String) = dao.rename(id, name, uri)
    suspend fun remove(id: String) = dao.delete(id)
}
