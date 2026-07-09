package com.voyagecam.app.ui.playback

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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.voyagecam.app.core.common.toContentUri
import com.voyagecam.app.ui.theme.SectionCard
import java.io.File
import kotlinx.coroutines.delay
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
    val primaryPlayer = rememberPlaybackPlayer(
        file = item.primaryFile,
        autoPlay = true,
        muted = false,
    )
    val secondaryPlayer = item.secondaryFile?.let {
        rememberPlaybackPlayer(
            file = it,
            autoPlay = true,
            muted = true,
        )
    }
    var isPlaying by remember(item.primaryFile.absolutePath, item.secondaryFile?.absolutePath) {
        mutableStateOf(true)
    }
    var syncStatus by remember(item.primaryFile.absolutePath, item.secondaryFile?.absolutePath) {
        mutableStateOf<PlaybackSyncStatus?>(null)
    }
    var lastAutoCorrectionOffsetMs by remember(item.primaryFile.absolutePath, item.secondaryFile?.absolutePath) {
        mutableStateOf<Long?>(null)
    }
    val hasSecondary = item.secondaryFile != null

    DisposableEffect(primaryPlayer, secondaryPlayer, hasSecondary) {
        if (!hasSecondary) {
            return@DisposableEffect onDispose { }
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }
        }
        primaryPlayer.addListener(listener)
        onDispose {
            primaryPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(primaryPlayer, secondaryPlayer, isPlaying, hasSecondary) {
        if (!hasSecondary) {
            syncStatus = null
            return@LaunchedEffect
        }
        while (secondaryPlayer != null) {
            val correction = playbackSyncCorrection(
                primaryPositionMs = primaryPlayer.currentPosition,
                secondaryPositionMs = secondaryPlayer.currentPosition,
                isPlaying = isPlaying,
            )
            syncStatus = correction.status
            if (correction.shouldCorrect) {
                lastAutoCorrectionOffsetMs = correction.status.offsetMs
                secondaryPlayer.seekTo(correction.targetSecondaryPositionMs)
                if (isPlaying) {
                    secondaryPlayer.play()
                }
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
            player = primaryPlayer,
            showNativeControls = !hasSecondary,
        )
        item.secondaryFile?.let {
            Spacer(modifier = Modifier.height(10.dp))
            VideoPane(
                label = item.secondaryLabel ?: "副画面",
                player = secondaryPlayer ?: return@let,
                showNativeControls = false,
            )
        }
        if (hasSecondary) {
            Spacer(modifier = Modifier.height(10.dp))
            SharedPlaybackControls(
                isPlaying = isPlaying,
                onTogglePlayback = {
                    val nextPlaying = !isPlaying
                    if (nextPlaying) {
                        secondaryPlayer?.seekTo(primaryPlayer.currentPosition)
                        primaryPlayer.play()
                        secondaryPlayer?.play()
                    } else {
                        primaryPlayer.pause()
                        secondaryPlayer?.pause()
                    }
                    isPlaying = nextPlaying
                },
                onRestart = {
                    primaryPlayer.seekTo(0L)
                    secondaryPlayer?.seekTo(0L)
                    primaryPlayer.play()
                    secondaryPlayer?.play()
                    isPlaying = true
                },
                onResync = {
                    secondaryPlayer?.seekTo(primaryPlayer.currentPosition)
                    lastAutoCorrectionOffsetMs = null
                    syncStatus = syncStatus?.copy(offsetMs = 0, requiresCorrection = false)
                    if (isPlaying) {
                        secondaryPlayer?.play()
                    }
                },
            )
            syncStatus?.let { status ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (status.requiresCorrection) {
                        "自动同步：检测到偏移 ${status.offsetMs}ms，正在纠偏"
                    } else if (lastAutoCorrectionOffsetMs != null) {
                        "自动同步：已校正上次偏移 ${lastAutoCorrectionOffsetMs}ms，当前偏移 ${status.offsetMs}ms"
                    } else {
                        "自动同步：当前偏移 ${status.offsetMs}ms"
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
    player: ExoPlayer,
    showNativeControls: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        AndroidVideoPlayer(player = player, showNativeControls = showNativeControls)
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
    player: ExoPlayer,
    showNativeControls: Boolean,
) {
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = showNativeControls
                controllerAutoShow = showNativeControls
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                this.player = player
            }
        },
        update = { playerView ->
            playerView.player = player
            playerView.useController = showNativeControls
            playerView.controllerAutoShow = showNativeControls
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    )
}

@Composable
private fun rememberPlaybackPlayer(
    file: File,
    autoPlay: Boolean,
    muted: Boolean,
): ExoPlayer {
    val context = LocalContext.current.applicationContext
    val player = remember(file.absolutePath, autoPlay, muted) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(file.toContentUri(context)))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = autoPlay
            volume = if (muted) 0f else 1f
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }
    return player
}
