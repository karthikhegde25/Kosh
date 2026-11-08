package com.kosh.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kosh.android.R

/** The privacy policy of the app as a whole. It works offline and only describes what Kosh does on the device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.privacy_title)) }, navigationIcon = { ReaderBackButton(onBack) }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.privacy_updated), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyLarge)
            Section(R.string.privacy_h_collect, R.string.privacy_b_collect)
            Section(R.string.privacy_h_storage, R.string.privacy_b_storage)
            Section(R.string.privacy_h_permissions, R.string.privacy_b_permissions)
            Section(R.string.privacy_h_sharing, R.string.privacy_b_sharing)
            Section(R.string.privacy_h_children, R.string.privacy_b_children)
            Section(R.string.privacy_h_control, R.string.privacy_b_control)
            Section(R.string.privacy_h_changes, R.string.privacy_b_changes)
            Text(
                stringResource(R.string.about_version, version),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun Section(headingRes: Int, bodyRes: Int) {
    Text(stringResource(headingRes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
    Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyMedium)
}
