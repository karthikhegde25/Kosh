package com.kosh.android.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosh.android.DraftViewModel
import com.kosh.android.R
import com.kosh.android.ui.components.rememberImagePicker
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private enum class CameraPermission { GRANTED, NOT_ASKED, DENIED, PERMANENTLY_DENIED }

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({
        try {
            cont.resume(future.get())
        } catch (e: Exception) {
            cont.resumeWithException(e)
        }
    }, ContextCompat.getMainExecutor(this))
}

@Composable
fun CameraScreen(
    draftVm: DraftViewModel,
    onCaptured: (pageId: String) -> Unit,
    onFinish: () -> Unit,
    onImported: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var permission by remember {
        mutableStateOf(if (hasCameraPermission(context)) CameraPermission.GRANTED else CameraPermission.NOT_ASKED)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = when {
            granted -> CameraPermission.GRANTED
            activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA) ->
                CameraPermission.PERMANENTLY_DENIED // "Don't ask again" (or blocked by policy)
            else -> CameraPermission.DENIED
        }
    }
    // The user may grant the permission in system settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (hasCameraPermission(context)) permission = CameraPermission.GRANTED
    }

    val importer = rememberImagePicker { draftVm.importImages(it, openEditor = true) }
    val notice by draftVm.notice.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        if (n.summary.added > 0) {
            draftVm.consumeNotice()
            onImported()
        }
    }

    if (permission == CameraPermission.GRANTED) {
        CameraContent(draftVm, onCaptured, onFinish, onBack, importer)
    } else {
        PermissionContent(
            state = permission,
            onAllow = { request.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onImport = importer,
            onBack = onBack,
        )
    }
}

@Composable
private fun PermissionContent(
    state: CameraPermission,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.camera_permission_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(
                when (state) {
                    CameraPermission.PERMANENTLY_DENIED -> R.string.camera_permission_blocked
                    CameraPermission.DENIED -> R.string.camera_permission_denied
                    else -> R.string.camera_permission_why
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        if (state == CameraPermission.PERMANENTLY_DENIED) {
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.camera_open_settings))
            }
        } else {
            Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(if (state == CameraPermission.DENIED) R.string.camera_try_again else R.string.camera_allow))
            }
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 8.dp)) {
            Text(stringResource(R.string.import_images))
        }
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.reader_back)) }
    }
}

@Composable
private fun CameraContent(
    draftVm: DraftViewModel,
    onCaptured: (String) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    onImport: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val draft by draftVm.draft.collectAsStateWithLifecycle()
    val pageCount = draft?.pages?.size ?: 0

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraFailed by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var flashOn by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val p = context.awaitCameraProvider()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            p.unbindAll()
            camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            provider = p
            imageCapture = capture
        } catch (e: Exception) {
            cameraFailed = true // no rear camera, camera in use, or hardware failure
        }
    }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll() } }

    val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
    LaunchedEffect(flashOn, imageCapture) {
        imageCapture?.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    fun capture() {
        val ic = imageCapture ?: return
        if (capturing) return // the shutter is disabled while a capture is running
        capturing = true
        scope.launch {
            val file = draftVm.newCaptureFile()
            ic.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        scope.launch {
                            val id = draftVm.addCapturedFile(file)
                            capturing = false
                            if (id != null) onCaptured(id)
                            else snackbar.showSnackbar(context.getString(R.string.camera_capture_failed))
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        capturing = false
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.camera_capture_failed)) }
                    }
                },
            )
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraFailed) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.camera_unavailable), color = Color.White, textAlign = TextAlign.Center)
                OutlinedButton(onClick = onImport, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.import_images))
                }
            }
        } else {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back), tint = Color.White)
                }
                Box(Modifier.weight(1f))
                if (hasFlash) {
                    IconButton(onClick = { flashOn = !flashOn }) {
                        Icon(
                            if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = stringResource(if (flashOn) R.string.camera_flash_on else R.string.camera_flash_off),
                            tint = Color.White,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.camera_guidance),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            Box(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.import_images), color = Color.White)
                }
                Surface(
                    modifier = Modifier
                        .size(76.dp)
                        .clickable(enabled = !capturing && imageCapture != null, onClickLabel = stringResource(R.string.camera_shutter)) { capture() },
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(4.dp, Color.Gray),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (capturing) CircularProgressIndicator(Modifier.size(32.dp), color = Color.DarkGray)
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (pageCount > 0) {
                        Button(onClick = onFinish) { Text(stringResource(R.string.camera_done, pageCount)) }
                    }
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp))
    }
}
