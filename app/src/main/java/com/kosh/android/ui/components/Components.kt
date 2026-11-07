package com.kosh.android.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kosh.android.R
import com.kosh.android.data.repository.ImportSummary

/**
 * Returns a function that opens the system Photo Picker (multi-select, images only).
 * No storage permission is needed. Cancelling returns an empty list and is ignored.
 */
@Composable
fun rememberImagePicker(onPicked: (List<Uri>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

@Composable
fun KoshLogo(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.kosh_logo),
        contentDescription = stringResource(R.string.logo_description),
        modifier = modifier.size(size).clip(RoundedCornerShape(size / 5)),
    )
}

fun ImportSummary.toMessage(context: Context): String {
    val res = context.resources
    return when {
        added == 0 -> res.getString(R.string.import_failed_all)
        failed == 0 -> res.getQuantityString(R.plurals.import_added, added, added)
        else -> res.getQuantityString(R.plurals.import_added, added, added) + " " +
            res.getQuantityString(R.plurals.import_some_failed, failed, failed)
    }
}
