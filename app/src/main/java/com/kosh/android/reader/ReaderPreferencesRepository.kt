package com.kosh.android.reader

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.readerPrefsStore: DataStore<Preferences> by preferencesDataStore(name = "kosh_reader_prefs")

enum class ReaderTheme { SYSTEM, LIGHT, DARK }
enum class ReaderLayout { CONTINUOUS, SINGLE_PAGE }

const val MIN_AUTO_SCROLL_SPEED = 0.25f
const val MAX_AUTO_SCROLL_SPEED = 4f

data class ReaderPreferences(
    val theme: ReaderTheme = ReaderTheme.SYSTEM,
    val layout: ReaderLayout = ReaderLayout.CONTINUOUS,
    /** Explicit setting: inverts the rendered page colours for night reading. Never changes the file. */
    val invertPages: Boolean = false,
    val autoScrollSpeed: Float = 1f,
)

class ReaderPreferencesRepository(private val context: Context) {

    val prefs: Flow<ReaderPreferences> = context.readerPrefsStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            ReaderPreferences(
                theme = p[THEME]?.let { n -> ReaderTheme.entries.firstOrNull { it.name == n } } ?: ReaderTheme.SYSTEM,
                layout = p[LAYOUT]?.let { n -> ReaderLayout.entries.firstOrNull { it.name == n } } ?: ReaderLayout.CONTINUOUS,
                invertPages = p[INVERT] ?: false,
                autoScrollSpeed = (p[SPEED] ?: 1f).coerceIn(MIN_AUTO_SCROLL_SPEED, MAX_AUTO_SCROLL_SPEED),
            )
        }

    suspend fun setTheme(theme: ReaderTheme) { context.readerPrefsStore.edit { it[THEME] = theme.name } }
    suspend fun setLayout(layout: ReaderLayout) { context.readerPrefsStore.edit { it[LAYOUT] = layout.name } }
    suspend fun setInvert(invert: Boolean) { context.readerPrefsStore.edit { it[INVERT] = invert } }
    suspend fun setAutoScrollSpeed(speed: Float) {
        context.readerPrefsStore.edit { it[SPEED] = speed.coerceIn(MIN_AUTO_SCROLL_SPEED, MAX_AUTO_SCROLL_SPEED) }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val LAYOUT = stringPreferencesKey("layout")
        val INVERT = booleanPreferencesKey("invert_pages")
        val SPEED = floatPreferencesKey("auto_scroll_speed")
    }
}
