package com.kosh.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosh.android.data.model.FilterMode
import com.kosh.android.data.repository.AppSettings
import com.kosh.android.data.repository.DraftRepository
import com.kosh.android.data.repository.SettingsRepository
import com.kosh.android.data.repository.ThemeMode
import com.kosh.android.pdf.ExportQuality
import com.kosh.android.pdf.PaperSize
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val draftRepo: DraftRepository,
) : ViewModel() {

    /** `null` until DataStore has delivered the first value. */
    val settings: StateFlow<AppSettings?> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    }

    fun setDefaultPaper(paper: PaperSize) {
        viewModelScope.launch { settingsRepo.setDefaultPaper(paper) }
    }

    fun setDefaultQuality(quality: ExportQuality) {
        viewModelScope.launch { settingsRepo.setDefaultQuality(quality) }
    }

    fun setDefaultFilter(filter: FilterMode) {
        viewModelScope.launch { settingsRepo.setDefaultFilter(filter) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepo.setOnboardingDone() }
    }

    fun clearDrafts() {
        viewModelScope.launch { draftRepo.clearAllDrafts() }
    }
}
