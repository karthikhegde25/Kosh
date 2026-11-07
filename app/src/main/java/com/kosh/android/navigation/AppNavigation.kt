package com.kosh.android.navigation

import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.kosh.android.DraftViewModel
import com.kosh.android.KoshViewModelFactory
import com.kosh.android.SettingsViewModel
import com.kosh.android.data.repository.ThemeMode
import com.kosh.android.scanner.ExportViewModel
import com.kosh.android.ui.screens.AboutScreen
import com.kosh.android.ui.screens.CameraScreen
import com.kosh.android.ui.screens.CropScreen
import com.kosh.android.ui.screens.EnhanceScreen
import com.kosh.android.ui.screens.ExportScreen
import com.kosh.android.ui.screens.ExportSuccessScreen
import com.kosh.android.ui.screens.MainScreen
import com.kosh.android.ui.screens.NoteEditorScreen
import com.kosh.android.ui.screens.OnboardingScreen
import com.kosh.android.ui.screens.PrivacyPolicyScreen
import com.kosh.android.ui.screens.PdfToolScreen
import com.kosh.android.viewer.PdfViewerScreen
import com.kosh.android.ui.screens.SettingsScreen
import com.kosh.android.ui.theme.KoshTheme

@Composable
fun KoshApp(incomingPdf: Uri? = null, onIncomingHandled: () -> Unit = {}) {
    // Called at the top of the activity, so both ViewModels are activity-scoped and
    // shared by every screen (the draft survives navigation and rotation).
    val settingsVm: SettingsViewModel = viewModel(factory = KoshViewModelFactory.Factory)
    val draftVm: DraftViewModel = viewModel(factory = KoshViewModelFactory.Factory)
    val settings by settingsVm.settings.collectAsStateWithLifecycle()

    val dark = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    KoshTheme(darkTheme = dark) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val loaded = settings
            if (loaded != null) {
                AppNavigation(settingsVm, draftVm, loaded.onboardingDone, incomingPdf, onIncomingHandled)
            }
        }
    }
}

@Composable
private fun AppNavigation(
    settingsVm: SettingsViewModel,
    draftVm: DraftViewModel,
    onboardingDone: Boolean,
    incomingPdf: Uri?,
    onIncomingHandled: () -> Unit,
) {
    val nav = rememberNavController()
    // Shared by the export and success screens (and survives rotation).
    val exportVm: ExportViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = KoshViewModelFactory.Factory)
    // Back from the camera to the Scanner tab, showing its Edit sub-tab with the new photos.
    val showEditor: () -> Unit = {
        draftVm.requestEdit()
        nav.popBackStack(Routes.HOME, inclusive = false)
    }
    // Evaluated once so finishing onboarding doesn't rebuild the graph.
    val start = remember { if (onboardingDone) Routes.HOME else Routes.ONBOARDING }

    // A PDF opened from another app (ACTION_VIEW / share sheet) goes straight to the reader.
    LaunchedEffect(incomingPdf) {
        if (incomingPdf != null) {
            nav.navigate(Routes.reader(incomingPdf))
            onIncomingHandled()
        }
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onContinue = {
                settingsVm.completeOnboarding()
                nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            MainScreen(
                draftVm = draftVm,
                onOpenPdf = { nav.navigate(Routes.reader(it)) },
                onOpenNote = { nav.navigate(Routes.note(it)) },
                onOpenCamera = { nav.navigate(Routes.CAMERA) },
                onOpenCrop = { nav.navigate(Routes.crop(it)) },
                onOpenEnhance = { nav.navigate(Routes.enhance(it)) },
                onSaveAsPdf = { nav.navigate(Routes.EXPORT) },
                onOpenTool = { nav.navigate(Routes.tool(it.id)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.TOOL,
            arguments = listOf(navArgument("toolId") { type = NavType.StringType }),
        ) {
            PdfToolScreen(
                onBack = { nav.popBackStack() },
                onOpenPdf = { nav.navigate(Routes.reader(it)) },
            )
        }
        composable(Routes.CAMERA) {
            CameraScreen(
                draftVm = draftVm,
                // After a capture, adjust the page; "Use page" returns here for the next shot.
                onCaptured = { nav.navigate(Routes.crop(it)) },
                onFinish = showEditor,
                onImported = showEditor,
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = Routes.CROP,
            arguments = listOf(navArgument("pageId") { type = NavType.StringType }),
        ) { entry ->
            val pageId = entry.arguments?.getString("pageId").orEmpty()
            CropScreen(pageId, draftVm, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
        }
        composable(
            route = Routes.ENHANCE,
            arguments = listOf(navArgument("pageId") { type = NavType.StringType }),
        ) { entry ->
            val pageId = entry.arguments?.getString("pageId").orEmpty()
            EnhanceScreen(pageId, draftVm, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
        }
        composable(Routes.EXPORT) {
            ExportScreen(
                draftVm = draftVm,
                exportVm = exportVm,
                onBack = { nav.popBackStack() },
                onSuccess = {
                    nav.navigate(Routes.SUCCESS) { popUpTo(Routes.EXPORT) { inclusive = true } }
                },
            )
        }
        composable(Routes.SUCCESS) {
            val leave = {
                // The PDF is saved; the temporary draft is no longer needed.
                draftVm.discardDraft()
                exportVm.reset()
            }
            ExportSuccessScreen(
                exportVm = exportVm,
                onOpen = { nav.navigate(Routes.reader(it)) },
                onScanMore = {
                    leave()
                    nav.navigate(Routes.CAMERA) { popUpTo(Routes.HOME) }
                },
                onDone = {
                    leave()
                    nav.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) {
            PdfViewerScreen(
                onBack = { nav.popBackStack() },
                onOpenPdf = { nav.navigate(Routes.reader(it)) },
            )
        }
        composable(
            route = Routes.NOTE,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) {
            NoteEditorScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settingsVm = settingsVm,
                onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
                onOpenAbout = { nav.navigate(Routes.ABOUT) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.PRIVACY) {
            PrivacyPolicyScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { nav.popBackStack() })
        }
    }
}
