package com.kosh.android.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * A text highlight made in the reader, stored beside the PDF (the file itself is never modified).
 * [page] is 1-based. [quadsJson] is a JSON array of {x,y,w,h} fractions of the page box (origin top-left, unrotated frame).
 */
@Entity(tableName = "highlights", indices = [Index("documentKey")])
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentKey: String,
    val page: Int,
    val text: String,
    val quadsJson: String,
    val color: Int,
    val note: String?,
    val createdAt: Long,
)

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE documentKey = :key ORDER BY page ASC, createdAt ASC")
    fun observe(key: String): Flow<List<HighlightEntity>>

    @Insert
    suspend fun insert(h: HighlightEntity): Long

    @Query("UPDATE highlights SET color = :color WHERE id = :id")
    suspend fun setColor(id: Long, color: Int)

    @Query("UPDATE highlights SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String?)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Version 1 -> 2: adds the highlights table. Existing data is untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `highlights` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`documentKey` TEXT NOT NULL, `page` INTEGER NOT NULL, `text` TEXT NOT NULL, " +
                "`quadsJson` TEXT NOT NULL, `color` INTEGER NOT NULL, `note` TEXT, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_documentKey` ON `highlights` (`documentKey`)")
    }
}
