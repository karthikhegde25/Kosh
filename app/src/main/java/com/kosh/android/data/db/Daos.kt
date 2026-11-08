package com.kosh.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE documentKey = :key ORDER BY pageIndex ASC, createdAt ASC")
    fun observe(key: String): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE documentKey = :key ORDER BY createdAt ASC")
    fun observe(key: String): Flow<List<PdfAnnotation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(annotation: PdfAnnotation)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ScanRecordDao {
    @Query("SELECT * FROM scan_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScanRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ScanRecord)

    @Query("UPDATE scan_records SET name = :name, uri = :uri WHERE id = :id")
    suspend fun rename(id: String, name: String, uri: String)

    @Query("DELETE FROM scan_records WHERE id = :id")
    suspend fun delete(id: String)
}
