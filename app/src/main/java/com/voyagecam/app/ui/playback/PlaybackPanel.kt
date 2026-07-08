package com.voyagecam.app.ui.playback

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import com.voyagecam.app.core.common.toContentUri
import com.voyagecam.app.ui.theme.SectionCard
import java.io.File
import kotlin.math.abs

data class PlaybackItem(
    val title: String,
    val subtitle: String,
    val primaryLabel: String,
    val primaryFile: File,
    val secondaryLabel: String? = null,
    val secondaryFile: File? = null,
)

@Composable
fun PlaybackPanel(
    item: PlaybackItem,
    onClose: () -> Unit,
    onOpenInSystem: () -> Unit,
) {
    var primaryVideoView by remember(item.primaryFile.absolutePath) { mutableStateOf<VideoView?>(null) }
    var secondaryVideoView by remember(item.secondaryFile?.absolutePath) { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember(item.primaryFile.absolutePath, item.secondaryFile?.absolutePath) { mutableStateOf(true) }
    var syncStatus by remember(item.primaryFile.absolutePath, item.secondaryFile?.absolutePath) {
        mutableStateOf<PlaybackSyncStatus?>(null)
    }
    val hasSecondary = item.secondaryFile != null

    LaunchedEffect(primaryVideoView, secondaryVideoView, isPlaying, hasSecondary) {
        if (!hasSecondary) {
            syncStatus = null
            return@LaunchedEffect
        }
        while (primaryVideoView != null && secondaryVideoView != null) {
            val primary = primaryVideoView
            val secondary = secondaryVideoView
            if (primary == null || secondary == null) break
            val status = playbackSyncStatus(
                primaryPositionMs = primary.currentPosition,
                secondaryPositionMs = secondary.currentPosition,
            )
            syncStatus = status
            if (isPlaying && status.requiresCorrection) {
                secondary.seekTo(primary.currentPosition)
            }
            delay(500L)
        }
    }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "应用内播放",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF163036),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4D6267),
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64777B),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(onClick = onClose) {
                Text("关闭")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        VideoPane(
            label = item.primaryLabel,
            file = item.primaryFile,
            autoPlay = true,
            showNativeControls = !hasSecondary,
            onVideoViewChanged = { primaryVideoView = it },
        )
        item.secondaryFile?.let { secondaryFile ->
            Spacer(modifier = Modifier.height(10.dp))
            VideoPane(
                label = item.secondaryLabel ?: "副画面",
                file = secondaryFile,
                autoPlay = true,
                showNativeControls = false,
                onVideoViewChanged = { secondaryVideoView = it },
            )
        }
        if (hasSecondary) {
            Spacer(modifier = Modifier.height(10.dp))
            SharedPlaybackControls(
                isPlaying = isPlaying,
                onTogglePlayback = {
                    val nextPlaying = !isPlaying
                    if (nextPlaying) {
                        secondaryVideoView?.seekTo(primaryVideoView?.currentPosition ?: 0)
                        primaryVideoView?.start()
                        secondaryVideoView?.start()
                    } else {
                        primaryVideoView?.pause()
                        secondaryVideoView?.pause()
                    }
                    isPlaying = nextPlaying
                },
                onRestart = {
                    primaryVideoView?.seekTo(0)
                    secondaryVideoView?.seekTo(0)
                    primaryVideoView?.start()
                    secondaryVideoView?.start()
                    isPlaying = true
                },
                onResync = {
                    secondaryVideoView?.seekTo(primaryVideoView?.currentPosition ?: 0)
                    syncStatus = syncStatus?.copy(offsetMs = 0, requiresCorrection = false)
                    if (isPlaying) {
                        secondaryVideoView?.start()
                    }
                },
            )
            syncStatus?.let { status ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (status.requiresCorrection) {
                        "双画面偏移 ${status.offsetMs}ms，已自动尝试纠偏"
                    } else {
                        "双画面偏移 ${status.offsetMs}ms"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.requiresCorrection || abs(status.offsetMs) >= 250) {
                        Color(0xFF9B2C2C)
                    } else {
                        Color(0xFF64777B)
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onOpenInSystem,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("系统播放器打开")
        }
    }
}

@Composable
private fun VideoPane(
    label: String,
    file: File,
    autoPlay: Boolean,
    showNativeControls: Boolean,
    onVideoViewChanged: (VideoView?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        AndroidVideoPlayer(
            file = file,
            autoPlay = autoPlay,
            showNativeControls = showNativeControls,
            onVideoViewChanged = onVideoViewChanged,
        )
    }
}

@Composable
private fun SharedPlaybackControls(
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onRestart: () -> Unit,
    onResync: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onTogglePlayback,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isPlaying) "暂停" else "播放")
            }
            OutlinedButton(
                onClick = onRestart,
                modifier = Modifier.weight(1f),
            ) {
                Text("归零")
            }
            OutlinedButton(
                onClick = onResync,
                modifier = Modifier.weight(1f),
            ) {
                Text("对齐")
            }
        }
    }
}

@Composable
private fun AndroidVideoPlayer(
    file: File,
    autoPlay: Boolean,
    showNativeControls: Boolean,
    onVideoViewChanged: (VideoView?) -> Unit,
) {
    val context = LocalContext.current
    var activeVideoView by remember { mutableStateOf<VideoView?>(null) }
    DisposableEffect(file.absolutePath) {
        onDispose {
            activeVideoView?.stopPlayback()
            onVideoViewChanged(null)
            activeVideoView = null
        }
    }

    AndroidView(
        factory = { viewContext ->
            VideoView(viewContext).apply {
                if (showNativeControls) {
                    setMediaController(MediaController(viewContext))
                }
            }
        },
        update = { videoView ->
            activeVideoView = videoView
            onVideoViewChanged(videoView)
            if (videoView.tag != file.absolutePath) {
                val uri = file.toContentUri(context)
                videoView.tag = file.absolutePath
                videoView.stopPlayback()
                videoView.setVideoURI(uri)
                videoView.setOnPreparedListener { player ->
                    player.isLooping = false
                    if (autoPlay) {
                        videoView.start()
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    )
}
