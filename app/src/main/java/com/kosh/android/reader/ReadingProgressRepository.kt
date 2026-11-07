package com.kosh.android.reader

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.readerStore: DataStore<Preferences> by preferencesDataStore(name = "kosh_reader")

/** Last viewed page per document. Writes use their own scope so leaving the reader can't cancel a save. */
class ReadingProgressRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun load(documentKey: String): Int? =
        runCatching { context.readerStore.data.first()[key(documentKey)] }.getOrNull()

    fun save(documentKey: String, pageIndex: Int) {
        scope.launch {
            runCatching { context.readerStore.edit { it[key(documentKey)] = pageIndex } }
        }
    }

    private fun key(documentKey: String) = intPreferencesKey("page_$documentKey")
}
