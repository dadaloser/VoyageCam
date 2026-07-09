package com.voyagecam.app.ui.preview

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.voyagecam.app.core.camera.DualCameraPreviewController
import com.voyagecam.app.core.camera.DualCameraSessionCoordinator
import com.voyagecam.app.core.camera.RearCameraPreviewController
import com.voyagecam.app.core.model.DualCameraDiagnostic
import androidx.compose.runtime.collectAsState

@Composable
fun RearCameraPreview(
    enabled: Boolean,
    frontInsetEnabled: Boolean = false,
    dualCameraSessionToken: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dualCameraSessionStatus by DualCameraSessionCoordinator.sessionStatus.collectAsState()
    val dualPreviewUnavailable = shouldFallbackToRearPreview(
        frontInsetEnabled = frontInsetEnabled,
        sessionToken = dualCameraSessionToken,
        sessionStatus = dualCameraSessionStatus,
    )

    if (enabled && frontInsetEnabled && !dualPreviewUnavailable) {
        DualCameraPreview(
            modifier = modifier,
            sessionToken = dualCameraSessionToken,
        )
        return
    }

    val controller = remember { RearCameraPreviewController(context) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            controller.destroy()
        }
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            controller.stop()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color(0xFF10282E)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !enabled -> PreviewMessage("后摄预览已暂停")
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED -> PreviewMessage("授权相机后显示后摄预览")
            else -> {
                AndroidView(
                    factory = { viewContext ->
                        PreviewView(viewContext).also { previewView ->
                            previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                            controller.start(previewView) { message ->
                                errorMessage = message
                            }
                        }
                    },
                    update = {},
                    modifier = Modifier.fillMaxSize(),
                )
                errorMessage?.let { message ->
                    PreviewMessage(message)
                }
                if (frontInsetEnabled && dualPreviewUnavailable) {
                    PreviewMessage(
                        dualCameraSessionStatus.lastDiagnostic?.summary()
                            ?: "前摄小窗暂不可用，已回落到后摄预览",
                    )
                }
            }
        }
    }
}

@Composable
private fun DualCameraPreview(
    modifier: Modifier = Modifier,
    sessionToken: Int,
) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    if (lifecycleOwner == null) {
        PreviewMessage("双摄预览需要 Activity 生命周期")
        return
    }
    val controller = remember(context, lifecycleOwner, sessionToken) {
        DualCameraPreviewController(
            context = context,
            lifecycleOwner = lifecycleOwner,
        )
    }
    var errorMessage by remember(sessionToken) { mutableStateOf<String?>(null) }

    DisposableEffect(controller) {
        onDispose {
            controller.destroy()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color(0xFF10282E)),
        contentAlignment = Alignment.Center,
    ) {
        var rearPreviewView by remember(sessionToken) { mutableStateOf<PreviewView?>(null) }
        var frontPreviewView by remember(sessionToken) { mutableStateOf<PreviewView?>(null) }

        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).also { previewView ->
                    previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                    rearPreviewView = previewView
                }
            },
            update = {},
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
            .width(126.dp)
            .height(72.dp)
            .background(Color(0xCC10282E)),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { viewContext ->
                    PreviewView(viewContext).also { previewView ->
                        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                        frontPreviewView = previewView
                    }
                },
                update = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

        LaunchedEffect(rearPreviewView, frontPreviewView, sessionToken) {
            val rear = rearPreviewView
            val front = frontPreviewView
            if (rear != null && front != null) {
                controller.start(
                    sessionToken = sessionToken,
                    rearPreviewView = rear,
                    frontPreviewView = front,
                ) { diagnostic ->
                    errorMessage = diagnostic.summary()
                }
            }
        }

        errorMessage?.let { message ->
            PreviewMessage(message)
        }
    }
}

@Composable
private fun PreviewMessage(message: String) {
    Text(
        text = message,
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(16.dp),
    )
}
