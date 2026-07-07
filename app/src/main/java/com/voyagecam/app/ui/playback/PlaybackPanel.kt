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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.voyagecam.app.core.common.toContentUri
import com.voyagecam.app.ui.theme.SectionCard
import java.io.File

data class PlaybackItem(
    val title: String,
    val subtitle: String,
    val file: File,
)

@Composable
fun PlaybackPanel(
    item: PlaybackItem,
    onClose: () -> Unit,
    onOpenInSystem: () -> Unit,
) {
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
        AndroidVideoPlayer(file = item.file)
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
private fun AndroidVideoPlayer(file: File) {
    val context = LocalContext.current
    var activeVideoView by remember { mutableStateOf<VideoView?>(null) }
    DisposableEffect(file.absolutePath) {
        onDispose {
            activeVideoView?.stopPlayback()
            activeVideoView = null
        }
    }

    AndroidView(
        factory = { viewContext ->
            VideoView(viewContext).apply {
                setMediaController(MediaController(viewContext))
            }
        },
        update = { videoView ->
            activeVideoView = videoView
            val uri = file.toContentUri(context)
            videoView.stopPlayback()
            videoView.setVideoURI(uri)
            videoView.setOnPreparedListener { player ->
                player.isLooping = false
                videoView.start()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    )
}
