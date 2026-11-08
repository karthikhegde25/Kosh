package com.kosh.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kosh.android.R
import com.kosh.android.ui.components.KoshLogo

private const val GITHUB_URL = "https://github.com/karthikhegde25/Kosh"

/** About Kosh: logo, name, developer, what the app does, its licence, and a link to the source on GitHub. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.about_title)) }, navigationIcon = { ReaderBackButton(onBack) }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KoshLogo(size = 96.dp)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
            if (version.isNotBlank()) {
                Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.about_developer), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

            Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.about_license),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                stringResource(R.string.about_credits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )

            IconButton(onClick = { uriHandler.openUri(GITHUB_URL) }, modifier = Modifier.padding(top = 20.dp).size(64.dp)) {
                Icon(
                    painterResource(R.drawable.ic_github),
                    contentDescription = stringResource(R.string.about_github),
                    modifier = Modifier.size(44.dp),
                )
            }
            Text(stringResource(R.string.about_github), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
