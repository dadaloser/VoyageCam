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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.EmergencyTrigger
import com.voyagecam.app.core.model.GpsTrackPoint
import com.voyagecam.app.core.model.GpsTrackSummary
import com.voyagecam.app.core.model.toGpsTrackSummary
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
    onRepairMissingSegments: () -> Unit,
    onOpen: (EmergencyEvent) -> Unit,
    onShare: (EmergencyEvent) -> Unit,
    onExport: (EmergencyEvent) -> Unit,
    onCancelExport: () -> Unit,
    onShareExport: (File) -> Unit,
    onDismissExport: () -> Unit,
    onOpenMap: (EmergencyEvent) -> Unit,
    onDelete: (EmergencyEvent) -> Unit,
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRepairMissingSegments) {
                    Text("修复")
                }
                OutlinedButton(onClick = onRefresh) {
                    Text("刷新")
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        exportState?.let { state ->
            EvidenceExportStatusPanel(
                state = state,
                onShare = onShareExport,
                onCancel = onCancelExport,
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
                    onDelete = onDelete,
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
    onCancel: () -> Unit,
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
                    text = buildString {
                        append(state.title)
                        if (state.currentItem.isNotBlank()) {
                            append(" · ")
                            append(state.currentItem)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4D6267),
                )
                LinearProgressIndicator(
                    progress = state.progressPercent / 100f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${state.progressPercent}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64777B),
                    )
                    OutlinedButton(onClick = onCancel) {
                        Text("取消导出")
                    }
                }
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

            is EvidenceExportState.Cancelled -> {
                Text(
                    text = "证据包导出已取消",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF163036),
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
    onDelete: (EmergencyEvent) -> Unit,
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
        if (event.gpsTrackPoints.isNotEmpty()) {
            GpsRoutePreview(event.gpsTrackPoints)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onExport(event) },
                enabled = event.segmentPaths.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text("导出证据包")
            }
            OutlinedButton(
                onClick = { onDelete(event) },
                modifier = Modifier.weight(1f),
            ) {
                Text("删除事件")
            }
        }
    }
}

@Composable
private fun GpsRoutePreview(points: List<GpsTrackPoint>) {
    val summary = points.toGpsTrackSummary() ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF3F7F6), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "路线预览 · ${summary.pointCount} 点",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        Text(
            text = "${summary.distanceText()} · ${summary.durationText()} · 均速 ${summary.averageSpeedText()}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4D6267),
        )
        summary.maxSpeedMetersPerSecond?.let { speed ->
            Text(
                text = "最高速度 ${speed.toKilometersPerHourText()}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        }
        Text(
            text = "起点 ${summary.startPoint.coordinateText()}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
        Text(
            text = "终点 ${summary.endPoint.coordinateText()}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
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

private fun GpsTrackSummary.distanceText(): String {
    return if (distanceMeters >= 1000.0) {
        String.format(Locale.getDefault(), "%.2fkm", distanceMeters / 1000.0)
    } else {
        String.format(Locale.getDefault(), "%.0fm", distanceMeters)
    }
}

private fun GpsTrackSummary.durationText(): String {
    val totalSeconds = durationMillis / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return when {
        minutes > 0L -> "${minutes}分${seconds}秒"
        seconds > 0L -> "${seconds}秒"
        else -> "瞬时"
    }
}

private fun GpsTrackSummary.averageSpeedText(): String {
    return averageSpeedMetersPerSecond.toFloat().toKilometersPerHourText()
}

private fun Float.toKilometersPerHourText(): String {
    return String.format(Locale.getDefault(), "%.0fkm/h", this * METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR)
}

private fun GpsTrackPoint.coordinateText(): String {
    return String.format(Locale.getDefault(), "%.5f, %.5f · %s", latitude, longitude, capturedAtMillis.asTime())
}

private const val METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR = 3.6f
