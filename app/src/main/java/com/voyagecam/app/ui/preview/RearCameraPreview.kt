package com.voyagecam.app.ui.preview

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    // Keep the inset stable across recomposition, orientation changes, and main/inset swaps.
    var insetPosition by rememberSaveable(stateSaver = PreviewInsetPosition.Saver) {
        mutableStateOf(PreviewInsetPosition.DEFAULT)
    }

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
        val density = LocalDensity.current
        val insetPlacement = rememberInsetPlacement(
            containerWidth = maxWidth,
            containerHeight = maxHeight,
            insetSize = insetSize,
            position = insetPosition,
            density = density,
        )

        PreviewSurfaceSlot(
            cameraDirection = CameraDirection.Rear,
            isPrimary = mainCameraDirection == CameraDirection.Rear,
            mirrored = false,
            insetWidth = insetSize.width,
            insetHeight = insetSize.height,
            insetPlacement = insetPlacement,
            onInsetPositionChanged = { insetPosition = it },
            content = rearPreview,
        )
        PreviewSurfaceSlot(
            cameraDirection = CameraDirection.Front,
            isPrimary = mainCameraDirection == CameraDirection.Front,
            mirrored = frontMirrorEnabled,
            insetWidth = insetSize.width,
            insetHeight = insetSize.height,
            insetPlacement = insetPlacement,
            onInsetPositionChanged = { insetPosition = it },
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
    insetPlacement: PreviewInsetPlacement,
    onInsetPositionChanged: (PreviewInsetPosition) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val slotTag = when {
        cameraDirection == CameraDirection.Rear && isPrimary -> "rear_main_preview"
        cameraDirection == CameraDirection.Front && isPrimary -> "front_main_preview"
        cameraDirection == CameraDirection.Rear -> "rear_inset_preview"
        else -> "front_inset_preview"
    }
    val coroutineScope = rememberCoroutineScope()
    var draggingOffset by remember(slotTag) { mutableStateOf<Offset?>(null) }
    var userSnapAnimationRunning by remember(slotTag) { mutableStateOf(false) }
    val animatedInsetOffset = remember(slotTag) {
        Animatable(
            initialValue = insetPlacement.offset,
            typeConverter = Offset.VectorConverter,
        )
    }
    LaunchedEffect(isPrimary, insetPlacement.offset, draggingOffset, userSnapAnimationRunning) {
        if (!isPrimary && draggingOffset == null && !userSnapAnimationRunning) {
            animatedInsetOffset.animateTo(
                targetValue = insetPlacement.offset,
                animationSpec = insetLayoutSyncAnimationSpec(),
            )
        }
    }
    Box(
        modifier = if (isPrimary) {
            Modifier
                .fillMaxSize()
                .testTag(slotTag)
        } else {
            Modifier
                .align(Alignment.TopStart)
                .offset {
                    val offset = draggingOffset ?: animatedInsetOffset.value
                    IntOffset(offset.x.roundToInt(), offset.y.roundToInt())
                }
                .width(insetWidth)
                .height(insetHeight)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                .background(Color(0xCC10282E))
                .pointerInput(
                    insetPlacement.minOffsetX,
                    insetPlacement.maxOffsetX,
                    insetPlacement.minOffsetY,
                    insetPlacement.maxOffsetY,
                ) {
                    detectDragGestures(
                        onDragStart = {
                            coroutineScope.launch {
                                animatedInsetOffset.stop()
                            }
                            draggingOffset = animatedInsetOffset.value
                        },
                        onDragEnd = {
                            val finalOffset = draggingOffset ?: animatedInsetOffset.value
                            val snappedPosition = insetPlacement.positionFor(
                                x = finalOffset.x,
                                y = finalOffset.y,
                            )
                            onInsetPositionChanged(snappedPosition)
                            coroutineScope.launch {
                                userSnapAnimationRunning = true
                                try {
                                    animatedInsetOffset.snapTo(finalOffset)
                                    draggingOffset = null
                                    animatedInsetOffset.animateTo(
                                        targetValue = insetPlacement.offsetFor(snappedPosition),
                                        animationSpec = insetSnapAnimationSpec(),
                                    )
                                } finally {
                                    userSnapAnimationRunning = false
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                userSnapAnimationRunning = true
                                try {
                                    animatedInsetOffset.snapTo(draggingOffset ?: animatedInsetOffset.value)
                                    draggingOffset = null
                                    animatedInsetOffset.animateTo(
                                        targetValue = insetPlacement.offset,
                                        animationSpec = insetSnapAnimationSpec(),
                                    )
                                } finally {
                                    userSnapAnimationRunning = false
                                }
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val currentOffset = draggingOffset ?: animatedInsetOffset.value
                        draggingOffset = Offset(
                            x = (currentOffset.x + dragAmount.x).coerceIn(
                                insetPlacement.minOffsetX,
                                insetPlacement.maxOffsetX,
                            ),
                            y = (currentOffset.y + dragAmount.y).coerceIn(
                                insetPlacement.minOffsetY,
                                insetPlacement.maxOffsetY,
                            ),
                        )
                    }
                }
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

private data class PreviewInsetPosition(
    val horizontalFraction: Float,
    val verticalFraction: Float,
) {
    companion object {
        val DEFAULT = PreviewInsetPosition(
            horizontalFraction = 1f,
            verticalFraction = 0f,
        )

        val Saver: Saver<PreviewInsetPosition, List<Float>> = Saver(
            save = { listOf(it.horizontalFraction, it.verticalFraction) },
            restore = { values ->
                PreviewInsetPosition(
                    horizontalFraction = values.getOrNull(0) ?: DEFAULT.horizontalFraction,
                    verticalFraction = values.getOrNull(1) ?: DEFAULT.verticalFraction,
                )
            },
        )
    }
}

private data class PreviewInsetPlacement(
    val offset: Offset,
    val minOffsetX: Float,
    val maxOffsetX: Float,
    val minOffsetY: Float,
    val maxOffsetY: Float,
) {
    fun offsetFor(position: PreviewInsetPosition): Offset {
        val horizontalTravel = maxOffsetX - minOffsetX
        val verticalTravel = maxOffsetY - minOffsetY
        return Offset(
            x = minOffsetX + horizontalTravel * position.horizontalFraction.coerceIn(0f, 1f),
            y = minOffsetY + verticalTravel * position.verticalFraction.coerceIn(0f, 1f),
        )
    }

    fun positionFor(
        x: Float,
        y: Float,
    ): PreviewInsetPosition {
        val clampedX = x.coerceIn(minOffsetX, maxOffsetX)
        val clampedY = y.coerceIn(minOffsetY, maxOffsetY)
        val horizontalTravel = maxOffsetX - minOffsetX
        val verticalTravel = maxOffsetY - minOffsetY
        val horizontalFraction = when {
            horizontalTravel <= 0f -> 0f
            else -> ((clampedX - minOffsetX) / horizontalTravel).coerceIn(0f, 1f)
        }
        val verticalFraction = when {
            verticalTravel <= 0f -> 0f
            else -> ((clampedY - minOffsetY) / verticalTravel).coerceIn(0f, 1f)
        }
        val snapEdge = listOf(
            PreviewInsetSnapEdge.Left to (clampedX - minOffsetX),
            PreviewInsetSnapEdge.Right to (maxOffsetX - clampedX),
            PreviewInsetSnapEdge.Top to (clampedY - minOffsetY),
            PreviewInsetSnapEdge.Bottom to (maxOffsetY - clampedY),
        ).minByOrNull { it.second }?.first ?: PreviewInsetSnapEdge.Right
        return PreviewInsetPosition(
            horizontalFraction = when (snapEdge) {
                PreviewInsetSnapEdge.Left -> 0f
                PreviewInsetSnapEdge.Right -> 1f
                PreviewInsetSnapEdge.Top,
                PreviewInsetSnapEdge.Bottom,
                -> horizontalFraction
            },
            verticalFraction = when (snapEdge) {
                PreviewInsetSnapEdge.Top -> 0f
                PreviewInsetSnapEdge.Bottom -> 1f
                PreviewInsetSnapEdge.Left,
                PreviewInsetSnapEdge.Right,
                -> verticalFraction
            },
        )
    }
}

private enum class PreviewInsetSnapEdge {
    Left,
    Right,
    Top,
    Bottom,
}

private fun rememberInsetPlacement(
    containerWidth: Dp,
    containerHeight: Dp,
    insetSize: PreviewInsetSize,
    position: PreviewInsetPosition,
    density: Density,
): PreviewInsetPlacement {
    val containerWidthPx = with(density) { containerWidth.toPx() }
    val containerHeightPx = with(density) { containerHeight.toPx() }
    val insetWidthPx = with(density) { insetSize.width.toPx() }
    val insetHeightPx = with(density) { insetSize.height.toPx() }
    val insetPaddingPx = with(density) { INSET_PADDING.toPx() }
    val minOffsetX = insetPaddingPx
    val minOffsetY = insetPaddingPx
    val maxOffsetX = (containerWidthPx - insetWidthPx - insetPaddingPx).coerceAtLeast(minOffsetX)
    val maxOffsetY = (containerHeightPx - insetHeightPx - insetPaddingPx).coerceAtLeast(minOffsetY)
    val horizontalTravel = maxOffsetX - minOffsetX
    val verticalTravel = maxOffsetY - minOffsetY
    return PreviewInsetPlacement(
        offset = Offset(
            x = minOffsetX + horizontalTravel * position.horizontalFraction.coerceIn(0f, 1f),
            y = minOffsetY + verticalTravel * position.verticalFraction.coerceIn(0f, 1f),
        ),
        minOffsetX = minOffsetX,
        maxOffsetX = maxOffsetX,
        minOffsetY = minOffsetY,
        maxOffsetY = maxOffsetY,
    )
}

private const val LANDSCAPE_ASPECT_RATIO = 16f / 9f
private const val PORTRAIT_ASPECT_RATIO = 9f / 16f
private const val INSET_WIDTH_FRACTION = 0.34f
private const val INSET_HEIGHT_FRACTION = 0.42f
private val INSET_PADDING = 12.dp
// Snap finishes a bit faster with a small rebound, while passive layout sync stays calmer.
private const val INSET_SNAP_STIFFNESS = 520f
private const val INSET_SNAP_DAMPING_RATIO = 0.82f
private const val INSET_LAYOUT_SYNC_STIFFNESS = 420f
private const val INSET_LAYOUT_SYNC_DAMPING_RATIO = 0.94f

private fun insetSnapAnimationSpec(): SpringSpec<Offset> = spring(
    stiffness = INSET_SNAP_STIFFNESS,
    dampingRatio = INSET_SNAP_DAMPING_RATIO,
)

private fun insetLayoutSyncAnimationSpec(): SpringSpec<Offset> = spring(
    stiffness = INSET_LAYOUT_SYNC_STIFFNESS,
    dampingRatio = INSET_LAYOUT_SYNC_DAMPING_RATIO,
)
