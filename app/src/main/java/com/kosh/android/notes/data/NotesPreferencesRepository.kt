package com.kosh.android.notes.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.notesStore: DataStore<Preferences> by preferencesDataStore(name = "kosh_notes")

enum class NoteSort { UPDATED, CREATED, TITLE }

data class NotesPrefs(
    val sort: NoteSort = NoteSort.UPDATED,
    val ascending: Boolean = false,
    val grid: Boolean = false,
)

class NotesPreferencesRepository(private val context: Context) {

    val prefs: Flow<NotesPrefs> = context.notesStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            NotesPrefs(
                sort = p[SORT]?.let { n -> NoteSort.entries.firstOrNull { it.name == n } } ?: NoteSort.UPDATED,
                ascending = p[ASCENDING] ?: false,
                grid = p[GRID] ?: false,
            )
        }

    suspend fun setSort(sort: NoteSort, ascending: Boolean) {
        context.notesStore.edit { it[SORT] = sort.name; it[ASCENDING] = ascending }
    }

    suspend fun setGrid(grid: Boolean) {
        context.notesStore.edit { it[GRID] = grid }
    }

    private companion object {
        val SORT = stringPreferencesKey("sort")
        val ASCENDING = booleanPreferencesKey("ascending")
        val GRID = booleanPreferencesKey("grid")
    }
}
