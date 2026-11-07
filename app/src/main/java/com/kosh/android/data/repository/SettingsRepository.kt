package com.kosh.android.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kosh.android.data.model.FilterMode
import com.kosh.android.pdf.ExportQuality
import com.kosh.android.pdf.PaperSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kosh_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboardingDone: Boolean = false,
    /** Defaults for new scans: used by the export screen and applied to newly added pages. */
    val defaultPaper: PaperSize = PaperSize.A4,
    val defaultQuality: ExportQuality = ExportQuality.STANDARD,
    val defaultFilter: FilterMode = FilterMode.ORIGINAL,
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            AppSettings(
                themeMode = prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                    ?: ThemeMode.SYSTEM,
                onboardingDone = prefs[ONBOARDING_DONE] ?: false,
                defaultPaper = prefs[PAPER]?.let { n -> PaperSize.entries.firstOrNull { it.name == n } } ?: PaperSize.A4,
                defaultQuality = prefs[QUALITY]?.let { n -> ExportQuality.entries.firstOrNull { it.name == n } }
                    ?: ExportQuality.STANDARD,
                defaultFilter = prefs[FILTER]?.let { n -> FilterMode.entries.firstOrNull { it.name == n } }
                    ?: FilterMode.ORIGINAL,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME] = mode.name }
    }

    suspend fun setDefaultPaper(paper: PaperSize) {
        context.dataStore.edit { it[PAPER] = paper.name }
    }

    suspend fun setDefaultQuality(quality: ExportQuality) {
        context.dataStore.edit { it[QUALITY] = quality.name }
    }

    suspend fun setDefaultFilter(filter: FilterMode) {
        context.dataStore.edit { it[FILTER] = filter.name }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[ONBOARDING_DONE] = true }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val PAPER = stringPreferencesKey("default_paper")
        val QUALITY = stringPreferencesKey("default_quality")
        val FILTER = stringPreferencesKey("default_filter")
    }
}
