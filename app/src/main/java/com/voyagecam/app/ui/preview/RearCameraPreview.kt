package com.voyagecam.app.ui.preview

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.voyagecam.app.R
import com.voyagecam.app.core.camera.DualCameraPreviewController
import com.voyagecam.app.core.camera.DualCameraSessionCoordinator
import com.voyagecam.app.core.camera.RearCameraPreviewController
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.data.settings.RecordingOrientationStrategy
import com.voyagecam.app.ui.dualCameraDiagnosticSummary

@Composable
fun RearCameraPreview(
    enabled: Boolean,
    previewPresentation: DualCameraPreviewPresentation = DualCameraPreviewPresentation(
        dualPreviewActive = false,
        sessionToken = 0,
        mainCameraDirection = CameraDirection.Rear,
    ),
    primaryCameraDirection: CameraDirection = CameraDirection.Rear,
    frontMirrorEnabled: Boolean = false,
    orientationStrategy: RecordingOrientationStrategy = RecordingOrientationStrategy.FollowSystem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val previewAspectRatio = previewContainerAspectRatio(
        orientationStrategy = orientationStrategy,
        configurationOrientation = configuration.orientation,
    )
    val dualCameraSessionStatus by DualCameraSessionCoordinator.sessionStatus.collectAsState()
    val dualPreviewUnavailable = shouldFallbackToRearPreview(
        dualPreviewActive = previewPresentation.dualPreviewActive,
        sessionToken = previewPresentation.sessionToken,
        sessionStatus = dualCameraSessionStatus,
    )

    if (enabled && previewPresentation.dualPreviewActive && !dualPreviewUnavailable) {
        DualCameraPreview(
            modifier = modifier,
            sessionToken = previewPresentation.sessionToken,
            mainCameraDirection = previewPresentation.mainCameraDirection,
            insetCameraDirection = previewPresentation.insetCameraDirection ?: CameraDirection.Front,
            frontMirrorEnabled = frontMirrorEnabled,
            orientationStrategy = orientationStrategy,
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
            .aspectRatio(previewAspectRatio)
            .background(Color(0xFF10282E))
            .testTag("rear_camera_preview"),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !enabled -> PreviewMessage(stringResource(R.string.preview_rear_paused))
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED -> PreviewMessage(stringResource(R.string.preview_camera_permission_needed))

            else -> {
                AndroidView(
                    factory = { viewContext ->
                        PreviewView(viewContext).also { previewView ->
                            previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                            previewView.scaleX = if (
                                primaryCameraDirection == CameraDirection.Front && frontMirrorEnabled
                            ) {
                                -1f
                            } else {
                                1f
                            }
                            controller.start(
                                previewView = previewView,
                                cameraDirection = primaryCameraDirection,
                                frontMirrorEnabled = frontMirrorEnabled,
                                orientationStrategy = orientationStrategy,
                            ) { message ->
                                errorMessage = message
                            }
                        }
                    },
                    update = { previewView ->
                        previewView.scaleX = if (
                            primaryCameraDirection == CameraDirection.Front && frontMirrorEnabled
                        ) {
                            -1f
                        } else {
                            1f
                        }
                        controller.start(
                            previewView = previewView,
                            cameraDirection = primaryCameraDirection,
                            frontMirrorEnabled = frontMirrorEnabled,
                            orientationStrategy = orientationStrategy,
                        ) { message ->
                            errorMessage = message
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                errorMessage?.let { message ->
                    PreviewMessage(message)
                }
                if (previewPresentation.dualPreviewActive && dualPreviewUnavailable) {
                    PreviewMessage(
                        dualCameraSessionStatus.lastDiagnostic?.let(context::dualCameraDiagnosticSummary)
                            ?: stringResource(R.string.preview_front_inset_unavailable),
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
    mainCameraDirection: CameraDirection,
    insetCameraDirection: CameraDirection,
    frontMirrorEnabled: Boolean,
    orientationStrategy: RecordingOrientationStrategy,
) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    if (lifecycleOwner == null) {
        PreviewMessage(stringResource(R.string.preview_dual_lifecycle_needed))
        return
    }
    val controller = remember(context, lifecycleOwner, sessionToken) {
        DualCameraPreviewController(
            context = context,
            lifecycleOwner = lifecycleOwner,
        )
    }
    var errorMessage by remember(sessionToken) { mutableStateOf<String?>(null) }
    var rearPreviewView by remember(sessionToken) { mutableStateOf<PreviewView?>(null) }
    var frontPreviewView by remember(sessionToken) { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(controller) {
        onDispose {
            controller.destroy()
        }
    }

    Box(modifier = modifier) {
        DualCameraPreviewLayout(
            mainCameraDirection = mainCameraDirection,
            insetCameraDirection = insetCameraDirection,
            frontMirrorEnabled = frontMirrorEnabled,
            orientationStrategy = orientationStrategy,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF10282E))
                .testTag("dual_camera_preview"),
            rearPreview = { surfaceModifier ->
                AndroidView(
                    factory = { viewContext ->
                        PreviewView(viewContext).also { previewView ->
                            previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                            rearPreviewView = previewView
                        }
                    },
                    update = { previewView ->
                        rearPreviewView = previewView
                    },
                    modifier = surfaceModifier,
                )
            },
            frontPreview = { surfaceModifier ->
                AndroidView(
                    factory = { viewContext ->
                        PreviewView(viewContext).also { previewView ->
                            previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                            previewView.scaleX = if (frontMirrorEnabled) -1f else 1f
                            frontPreviewView = previewView
                        }
                    },
                    update = { previewView ->
                        previewView.scaleX = if (frontMirrorEnabled) -1f else 1f
                        frontPreviewView = previewView
                    },
                    modifier = surfaceModifier,
                )
            },
        )

        LaunchedEffect(rearPreviewView, frontPreviewView, sessionToken) {
            val rear = rearPreviewView
            val front = frontPreviewView
            if (rear != null && front != null) {
                controller.start(
                    sessionToken = sessionToken,
                    rearPreviewView = rear,
                    frontPreviewView = front,
                ) { diagnostic ->
                    errorMessage = context.dualCameraDiagnosticSummary(diagnostic)
                }
            }
        }

        errorMessage?.let { message ->
            PreviewMessage(message)
        }
    }
}

@Composable
internal fun DualCameraPreviewLayout(
    mainCameraDirection: CameraDirection,
    insetCameraDirection: CameraDirection,
    frontMirrorEnabled: Boolean,
    orientationStrategy: RecordingOrientationStrategy,
    modifier: Modifier = Modifier,
    rearPreview: @Composable (Modifier) -> Unit,
    frontPreview: @Composable (Modifier) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val previewAspectRatio = previewContainerAspectRatio(
        orientationStrategy = orientationStrategy,
        configurationOrientation = configuration.orientation,
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(previewAspectRatio),
        contentAlignment = Alignment.Center,
    ) {
        val insetSize = rememberInsetSize(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            previewAspectRatio = previewAspectRatio,
        )

        PreviewSurfaceSlot(
            cameraDirection = CameraDirection.Rear,
            isPrimary = mainCameraDirection == CameraDirection.Rear,
            mirrored = false,
            insetWidth = insetSize.width,
            insetHeight = insetSize.height,
            content = rearPreview,
        )
        PreviewSurfaceSlot(
            cameraDirection = CameraDirection.Front,
            isPrimary = mainCameraDirection == CameraDirection.Front,
            mirrored = frontMirrorEnabled,
            insetWidth = insetSize.width,
            insetHeight = insetSize.height,
            content = frontPreview,
        )
    }
}

@Composable
private fun BoxWithConstraintsScope.PreviewSurfaceSlot(
    cameraDirection: CameraDirection,
    isPrimary: Boolean,
    mirrored: Boolean,
    insetWidth: Dp,
    insetHeight: Dp,
    content: @Composable (Modifier) -> Unit,
) {
    val slotTag = when {
        cameraDirection == CameraDirection.Rear && isPrimary -> "rear_main_preview"
        cameraDirection == CameraDirection.Front && isPrimary -> "front_main_preview"
        cameraDirection == CameraDirection.Rear -> "rear_inset_preview"
        else -> "front_inset_preview"
    }
    Box(
        modifier = if (isPrimary) {
            Modifier
                .fillMaxSize()
                .testTag(slotTag)
        } else {
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .width(insetWidth)
                .height(insetHeight)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                .background(Color(0xCC10282E))
                .testTag(slotTag)
        },
    ) {
        content(Modifier.fillMaxSize())
        PreviewBadge(
            text = stringResource(
                if (isPrimary) {
                    R.string.preview_slot_main
                } else {
                    R.string.preview_slot_inset
                },
                stringResource(
                    when (cameraDirection) {
                        CameraDirection.Rear -> R.string.label_camera_rear
                        CameraDirection.Front -> R.string.label_camera_front
                    },
                ),
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
        )
        if (cameraDirection == CameraDirection.Front && mirrored) {
            PreviewBadge(
                text = stringResource(R.string.preview_mirror_badge),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .testTag("front_preview_mirror_badge"),
            )
        }
    }
}

@Composable
private fun PreviewBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x9910282E))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
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

internal fun previewContainerAspectRatio(
    orientationStrategy: RecordingOrientationStrategy,
    configurationOrientation: Int,
): Float {
    return when (orientationStrategy) {
        RecordingOrientationStrategy.FixedLandscapeDriving -> LANDSCAPE_ASPECT_RATIO
        RecordingOrientationStrategy.FollowSystem -> {
            if (configurationOrientation == Configuration.ORIENTATION_PORTRAIT) {
                PORTRAIT_ASPECT_RATIO
            } else {
                LANDSCAPE_ASPECT_RATIO
            }
        }
    }
}

private fun rememberInsetSize(
    maxWidth: Dp,
    maxHeight: Dp,
    previewAspectRatio: Float,
): PreviewInsetSize {
    val maxInsetWidth = maxWidth * INSET_WIDTH_FRACTION
    val maxInsetHeight = maxHeight * INSET_HEIGHT_FRACTION
    var insetWidth = maxInsetWidth
    var insetHeight = insetWidth / previewAspectRatio
    if (insetHeight > maxInsetHeight) {
        insetHeight = maxInsetHeight
        insetWidth = insetHeight * previewAspectRatio
    }
    return PreviewInsetSize(width = insetWidth, height = insetHeight)
}

private data class PreviewInsetSize(
    val width: Dp,
    val height: Dp,
)

private const val LANDSCAPE_ASPECT_RATIO = 16f / 9f
private const val PORTRAIT_ASPECT_RATIO = 9f / 16f
private const val INSET_WIDTH_FRACTION = 0.34f
private const val INSET_HEIGHT_FRACTION = 0.42f
