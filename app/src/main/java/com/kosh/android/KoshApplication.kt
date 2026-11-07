package com.kosh.android

import android.app.Application
import com.kosh.android.data.repository.DraftRepository
import com.kosh.android.data.repository.SettingsRepository
import androidx.room.Room
import com.kosh.android.data.db.AppDatabase
import com.kosh.android.data.db.ReaderDataRepository
import com.kosh.android.data.db.ScanRecordRepository
import com.kosh.android.notes.data.NoteImageStore
import com.kosh.android.notes.data.NoteRepository
import com.kosh.android.notes.data.NotesDatabase
import com.kosh.android.notes.data.NotesPreferencesRepository
import com.kosh.android.reader.ReadingProgressRepository

class KoshApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // PdfBox needs its bundled fonts and glyph tables, which it loads from the app's assets.
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
    }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val draftRepository: DraftRepository by lazy { DraftRepository(this) }
    val readingProgressRepository: ReadingProgressRepository by lazy { ReadingProgressRepository(this) }

    private val notesDatabase: NotesDatabase by lazy {
        Room.databaseBuilder(this, NotesDatabase::class.java, "kosh_notes.db").build()
    }
    private val appDatabase: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "kosh_app.db")
            .addMigrations(com.kosh.android.data.db.MIGRATION_1_2)
            .build()
    }
    val readerDataRepository: ReaderDataRepository by lazy {
        ReaderDataRepository(appDatabase.bookmarkDao(), appDatabase.annotationDao())
    }
    val highlightDao: com.kosh.android.data.db.HighlightDao by lazy { appDatabase.highlightDao() }
    val scanRecordRepository: ScanRecordRepository by lazy { ScanRecordRepository(appDatabase.scanRecordDao()) }
    val noteImageStore: NoteImageStore by lazy { NoteImageStore(java.io.File(filesDir, "note_images")) }
    val noteRepository: NoteRepository by lazy { NoteRepository(notesDatabase.noteDao()) }
    val notesPreferences: NotesPreferencesRepository by lazy { NotesPreferencesRepository(this) }
}
