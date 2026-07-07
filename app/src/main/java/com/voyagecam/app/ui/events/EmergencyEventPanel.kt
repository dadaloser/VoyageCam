package com.voyagecam.app.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.EmergencyTrigger
import com.voyagecam.app.ui.theme.SectionCard
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EmergencyEventPanel(
    events: List<EmergencyEvent>,
    exportState: EvidenceExportState?,
    onRefresh: () -> Unit,
    onOpen: (EmergencyEvent) -> Unit,
    onShare: (EmergencyEvent) -> Unit,
    onExport: (EmergencyEvent) -> Unit,
    onShareExport: (File) -> Unit,
    onDismissExport: () -> Unit,
    onOpenMap: (EmergencyEvent) -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "紧急事件",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF163036),
            )
            OutlinedButton(onClick = onRefresh) {
                Text("刷新")
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        exportState?.let { state ->
            EvidenceExportStatusPanel(
                state = state,
                onShare = onShareExport,
                onDismiss = onDismissExport,
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        if (events.isEmpty()) {
            Text(
                text = "暂无紧急事件。手动锁定或碰撞触发后，这里会记录触发时间和关联片段。",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        } else {
            events.forEachIndexed { index, event ->
                EmergencyEventRow(
                    event = event,
                    onOpen = onOpen,
                    onShare = onShare,
                    onExport = onExport,
                    onOpenMap = onOpenMap,
                )
                if (index != events.lastIndex) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE1E8EA)),
                    )
                }
            }
        }
    }
}

@Composable
private fun EvidenceExportStatusPanel(
    state: EvidenceExportState,
    onShare: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEAF4F0), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            is EvidenceExportState.Running -> {
                Text(
                    text = "正在导出证据包",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF163036),
                )
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4D6267),
                )
            }

            is EvidenceExportState.Ready -> {
                Text(
                    text = "证据包已就绪",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF163036),
                )
                Text(
                    text = "${state.file.name} · ${state.clipCount} 段视频 · ${state.file.length().asFileSize()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4D6267),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onShare(state.file) }) {
                        Text("分享证据包")
                    }
                    OutlinedButton(onClick = onDismiss) {
                        Text("收起")
                    }
                }
            }

            is EvidenceExportState.Failed -> {
                Text(
                    text = "证据包导出失败",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF9B2C2C),
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4D6267),
                )
                OutlinedButton(onClick = onDismiss) {
                    Text("收起")
                }
            }
        }
    }
}

@Composable
private fun EmergencyEventRow(
    event: EmergencyEvent,
    onOpen: (EmergencyEvent) -> Unit,
    onShare: (EmergencyEvent) -> Unit,
    onExport: (EmergencyEvent) -> Unit,
    onOpenMap: (EmergencyEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = event.trigger.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (event.trigger == EmergencyTrigger.Collision) Color(0xFF9B2C2C) else Color(0xFF163036),
            )
            Text(
                text = "${event.segmentPaths.size} 段",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        }
        Text(
            text = event.triggeredAtMillis.asTime(),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
        event.collisionSummary()?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9B2C2C),
            )
        }
        event.locationSummary()?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4D6267),
            )
        }
        val segmentText = event.segmentPaths.take(3).joinToString(separator = "\n")
        if (segmentText.isNotBlank()) {
            Text(
                text = segmentText,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4D6267),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onOpen(event) },
                enabled = event.segmentPaths.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text("播放首段")
            }
            OutlinedButton(
                onClick = { onShare(event) },
                enabled = event.segmentPaths.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text("分享全部")
            }
            OutlinedButton(
                onClick = { onOpenMap(event) },
                enabled = event.hasLocation(),
                modifier = Modifier.weight(1f),
            ) {
                Text("地图")
            }
        }
        OutlinedButton(
            onClick = { onExport(event) },
            enabled = event.segmentPaths.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("导出证据包")
        }
    }
}

private fun Long.asTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))
}

private fun Long.asFileSize(): String {
    val kb = this / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.getDefault(), "%.1fGB", gb)
        mb >= 1.0 -> String.format(Locale.getDefault(), "%.1fMB", mb)
        kb >= 1.0 -> String.format(Locale.getDefault(), "%.0fKB", kb)
        else -> "${this}B"
    }
}
