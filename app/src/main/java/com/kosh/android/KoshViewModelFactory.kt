package com.kosh.android

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kosh.android.notes.data.NoteImageImporter
import com.kosh.android.notes.export.AndroidNoteExporter
import com.kosh.android.notes.ui.NoteEditorViewModel
import com.kosh.android.notes.ui.NotesViewModel
import com.kosh.android.pdf.PdfGenerator
import com.kosh.android.pdf.ScanFileOps
import com.kosh.android.pdflist.PdfFileOps
import com.kosh.android.scanner.ExportViewModel
import com.kosh.android.scanner.RecentScansViewModel
import com.kosh.android.pdflist.PdfFinder
import com.kosh.android.reader.PdfDocumentRepository
import com.kosh.android.reader.ReaderViewModel
import com.kosh.android.reader.ReaderPreferencesRepository
import com.kosh.android.reader.annotation.AnnotatedPdfExporter
import com.kosh.android.tools.DocxPdfEngine
import com.kosh.android.tools.XlsxPdfEngine
import com.kosh.android.tools.ImageExtractEngine
import com.kosh.android.tools.PdfBoxEngine
import com.kosh.android.tools.PdfEditEngine
import com.kosh.android.tools.PdfToolViewModel
import com.kosh.android.tools.PdfToolsRunner
import com.kosh.android.viewer.ViewerPreferencesRepository
import com.kosh.android.viewer.ViewerViewModel

object KoshViewModelFactory {
    val Factory = viewModelFactory {
        initializer {
            val app = this[APPLICATION_KEY] as KoshApplication
            DraftViewModel(app.draftRepository, app.settingsRepository)
        }
        initializer {
            val app = this[APPLICATION_KEY] as KoshApplication
            SettingsViewModel(app.settingsRepository, app.draftRepository)
        }
        initializer {
            val app = this[APPLICATION_KEY] as KoshApplication
            PdfListViewModel(PdfFinder(), PdfFileOps(app), com.kosh.android.tools.OfficePdfConverter(app))
        }
        initializer {
            val app = this[APPLICATION_KEY] as KoshApplication
            ReaderViewModel(
                PdfDocumentRepository(app),
                app.readingProgressRepository,
                app.readerDataRepository,
                ReaderPreferencesRepository(app),
                AnnotatedPdfExporter(app.contentResolver),
                createSavedStateHandle(),
            )
        }
        initializer {
            val app = this[APPLICATION_KEY] as KoshApplication
            PdfToolsRunner(app).let { runner ->
                val box = PdfBoxEngine(app, runner)
                PdfToolViewModel(PdfDocumentRepository(app), runner, PdfEditEngine(app), box, DocxPdfEngine(app, runner, box), XlsxPdfEngine(app, runner), ImageExtractEngine(app, runner), createSavedStateHandle())
            }
        }
        initializer {
            val app = this[APPLICATION_KEY] as KoshApplication
            com.kosh.android.imagetools.ImageToolViewModel(app, createSavedStateHandle())
        }
        initializer {
            val app = this[APPLICATION_KEY] as KoshApplication
            ViewerViewModel(
                app,
                ViewerPreferencesRepository(app),
                app.readingProgressRepository,
                app.readerDataRepository,
                app.highlightDao,
                createSavedStateHandle(),
            )
        }
        initializer {
            val app = this[APPLICATION_KEY] as KoshApplication
            NotesViewModel(app.noteRepository, app.notesPreferences, app.noteImageStore)
        }
        initializer {
            val app = this[APPLICATION_KEY] as KoshApplication
            NoteEditorViewModel(
                app.noteRepository,
                AndroidNoteExporter(app.contentResolver),
                app.noteImageStore,
                NoteImageImporter(app.contentResolver, app.cacheDir),
                java.io.File(app.cacheDir, "shared_notes"),
                createSavedStateHandle(),
            )
        }
        initializer {
            val app = this[APPLICATION_KEY] as KoshApplication
            ExportViewModel(
                PdfGenerator(app.contentResolver),
                ScanFileOps(app.contentResolver),
                app.scanRecordRepository,
                app.settingsRepository,
            )
        }
        initializer {
            val app = this[APPLICATION_KEY] as KoshApplication
            RecentScansViewModel(app.scanRecordRepository, ScanFileOps(app.contentResolver))
        }
    }
}
