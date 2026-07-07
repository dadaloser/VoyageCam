package com.voyagecam.app

import android.Manifest
import android.content.pm.PackageManager
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

@Composable
fun RearCameraPreview(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
            !enabled -> PreviewMessage("录制中，后摄画面由前台服务占用")
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED -> PreviewMessage("授权相机后显示后摄预览")
            else -> {
                AndroidView(
                    factory = { viewContext ->
                        TextureView(viewContext).also { textureView ->
                            controller.start(textureView) { message ->
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
            }
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
