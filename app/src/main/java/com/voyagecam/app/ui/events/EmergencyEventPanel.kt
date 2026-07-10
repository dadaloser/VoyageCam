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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voyagecam.app.R
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.EmergencyTrigger
import com.voyagecam.app.core.model.GpsTrackPoint
import com.voyagecam.app.core.model.GpsTrackSummary
import com.voyagecam.app.core.model.toGpsTrackSummary
import com.voyagecam.app.ui.export.ExportStatusPanel
import com.voyagecam.app.ui.labelRes
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
    val context = LocalContext.current
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.events_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF163036),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRepairMissingSegments) {
                    Text(stringResource(R.string.events_repair))
                }
                OutlinedButton(onClick = onRefresh) {
                    Text(stringResource(R.string.settings_refresh))
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        exportState?.let { state ->
            ExportStatusPanel(
                state = state,
                onShare = onShareExport,
                onCancel = onCancelExport,
                onDismiss = onDismissExport,
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.events_empty),
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
private fun EmergencyEventRow(
    event: EmergencyEvent,
    onOpen: (EmergencyEvent) -> Unit,
    onShare: (EmergencyEvent) -> Unit,
    onExport: (EmergencyEvent) -> Unit,
    onOpenMap: (EmergencyEvent) -> Unit,
    onDelete: (EmergencyEvent) -> Unit,
) {
    val context = LocalContext.current
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
                text = stringResource(event.trigger.labelRes()),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (event.trigger == EmergencyTrigger.Collision) Color(0xFF9B2C2C) else Color(0xFF163036),
            )
            Text(
                text = stringResource(R.string.events_segment_count, event.segmentPaths.size),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        }
        Text(
            text = event.triggeredAtMillis.asTime(),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
        event.collisionSummary(context)?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9B2C2C),
            )
        }
        event.locationSummary(context)?.let { summary ->
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
                Text(stringResource(R.string.events_open_first))
            }
            OutlinedButton(
                onClick = { onShare(event) },
                enabled = event.segmentPaths.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.events_share_all))
            }
            OutlinedButton(
                onClick = { onOpenMap(event) },
                enabled = event.hasLocation(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.events_map))
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
                Text(stringResource(R.string.events_export_button))
            }
            OutlinedButton(
                onClick = { onDelete(event) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.events_delete_button))
            }
        }
    }
}

@Composable
private fun GpsRoutePreview(points: List<GpsTrackPoint>) {
    val context = LocalContext.current
    val summary = points.toGpsTrackSummary() ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF3F7F6), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.events_route_preview, summary.pointCount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        Text(
            text = stringResource(
                R.string.events_route_summary,
                summary.distanceText(),
                summary.durationText(context),
                summary.averageSpeedText(context),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4D6267),
        )
        summary.maxSpeedMetersPerSecond?.let { speed ->
            Text(
                text = stringResource(R.string.events_route_max_speed, speed.toKilometersPerHourText(context)),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        }
        Text(
            text = stringResource(R.string.events_route_start, summary.startPoint.coordinateText(context)),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
        Text(
            text = stringResource(R.string.events_route_end, summary.endPoint.coordinateText(context)),
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

private fun GpsTrackSummary.durationText(context: android.content.Context): String {
    val totalSeconds = durationMillis / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return when {
        minutes > 0L -> context.getString(R.string.events_duration_minutes_seconds, minutes, seconds)
        seconds > 0L -> context.getString(R.string.events_duration_seconds, seconds)
        else -> context.getString(R.string.events_duration_instant)
    }
}

private fun GpsTrackSummary.averageSpeedText(context: android.content.Context): String {
    return averageSpeedMetersPerSecond.toFloat().toKilometersPerHourText(context)
}

private fun Float.toKilometersPerHourText(context: android.content.Context): String {
    return String.format(
        Locale.getDefault(),
        context.getString(R.string.events_speed_kmh),
        this * METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR,
    )
}

private fun GpsTrackPoint.coordinateText(context: android.content.Context): String {
    val bearingText = bearingDegrees?.let {
        context.getString(R.string.events_bearing, it)
    }.orEmpty()
    return context.getString(
        R.string.events_coordinate_text,
        latitude,
        longitude,
        bearingText,
        capturedAtMillis.asTime(),
    )
}

private const val METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR = 3.6f
