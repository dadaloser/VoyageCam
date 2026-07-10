package com.voyagecam.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voyagecam.app.R
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.core.model.RecordingSegment
import com.voyagecam.app.feature.evidence.RecordingClipExportMode
import com.voyagecam.app.ui.events.EvidenceExportState
import com.voyagecam.app.ui.export.ExportStatusPanel
import com.voyagecam.app.ui.labelRes
import com.voyagecam.app.ui.theme.SectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SegmentHistoryPanel(
    segments: List<RecordingSegment>,
    allSegments: List<RecordingSegment>,
    totalSegmentCount: Int,
    exportState: EvidenceExportState?,
    availableDays: List<String>,
    selectedDay: String?,
    selectedCameraFilter: SegmentCameraFilter,
    selectedLockFilter: SegmentLockFilter,
    onSelectedDayChanged: (String?) -> Unit,
    onCameraFilterChanged: (SegmentCameraFilter) -> Unit,
    onLockFilterChanged: (SegmentLockFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (RecordingSegment) -> Unit,
    onShare: (RecordingSegment) -> Unit,
    onExportGroup: (String, RecordingClipExportMode) -> Unit,
    onCancelExport: () -> Unit,
    onShareExport: (java.io.File) -> Unit,
    onDismissExport: () -> Unit,
    onUnlock: (RecordingSegment) -> Unit,
    onDelete: (RecordingSegment) -> Unit,
) {
    val visibleGroupKeys = remember(segments) { segments.map { it.groupKey }.toSet() }
    val allGroups = remember(allSegments) { allSegments.toTimelineGroups() }
    val visibleGroups = remember(allGroups, visibleGroupKeys) {
        allGroups.filter { it.groupKey in visibleGroupKeys }
    }
    var pendingExportGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingExportGroup = visibleGroups.firstOrNull { it.groupKey == pendingExportGroupKey }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF163036),
            )
            OutlinedButton(onClick = onRefresh) {
                Text(stringResource(R.string.settings_refresh))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        SegmentFilterPanel(
            availableDays = availableDays,
            selectedDay = selectedDay,
            selectedCameraFilter = selectedCameraFilter,
            selectedLockFilter = selectedLockFilter,
            onSelectedDayChanged = onSelectedDayChanged,
            onCameraFilterChanged = onCameraFilterChanged,
            onLockFilterChanged = onLockFilterChanged,
        )
        Spacer(modifier = Modifier.height(12.dp))
        exportState?.let { state ->
            ExportStatusPanel(
                state = state,
                onShare = onShareExport,
                onCancel = onCancelExport,
                onDismiss = onDismissExport,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (totalSegmentCount == 0) {
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        } else if (segments.isEmpty()) {
            Text(
                text = stringResource(R.string.history_filtered_empty),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        } else {
            Text(
                text = stringResource(
                    R.string.history_summary,
                    visibleGroups.size,
                    allGroups.size,
                    segments.size,
                    totalSegmentCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
            Spacer(modifier = Modifier.height(12.dp))
            TimelineGroupList(
                groups = visibleGroups,
                onOpen = onOpen,
                onShare = onShare,
                onExport = { pendingExportGroupKey = it },
                onUnlock = onUnlock,
                onDelete = onDelete,
            )
        }
    }

    pendingExportGroup?.let { group ->
        HistoryExportModeDialog(
            group = group,
            onDismiss = { pendingExportGroupKey = null },
            onExport = { mode ->
                pendingExportGroupKey = null
                onExportGroup(group.groupKey, mode)
            },
        )
    }
}

@Composable
private fun TimelineGroupList(
    groups: List<RecordingSegmentGroup>,
    onOpen: (RecordingSegment) -> Unit,
    onShare: (RecordingSegment) -> Unit,
    onExport: (String) -> Unit,
    onUnlock: (RecordingSegment) -> Unit,
    onDelete: (RecordingSegment) -> Unit,
) {
    var previousDay: String? = null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        groups.forEach { group ->
            if (group.day != previousDay) {
                Text(
                    text = group.day,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF163036),
                )
                previousDay = group.day
            }
            TimelineGroupCard(
                group = group,
                onOpen = onOpen,
                onShare = onShare,
                onExport = onExport,
                onUnlock = onUnlock,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun TimelineGroupCard(
    group: RecordingSegmentGroup,
    onOpen: (RecordingSegment) -> Unit,
    onShare: (RecordingSegment) -> Unit,
    onExport: (String) -> Unit,
    onUnlock: (RecordingSegment) -> Unit,
    onDelete: (RecordingSegment) -> Unit,
) {
    val context = LocalContext.current
    val primarySegment = group.rearSegment ?: group.frontSegment
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(78.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = group.lastModifiedMillis.asTimelineTime(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF163036),
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(96.dp)
                    .background(Color(0xFFD4E3DE)),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFFF1F6F4), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.history_group_export_title, group.lastModifiedMillis.asTime(), group.relationLabel(context)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF163036),
                    )
                    Text(
                        text = stringResource(
                            R.string.history_group_size_summary,
                            group.segmentCount,
                            group.totalSizeBytes.asFileSize(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64777B),
                    )
                }
                Text(
                    text = group.groupKey.substringAfterLast('/'),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64777B),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { primarySegment?.let(onOpen) },
                    enabled = primarySegment != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.history_open))
                }
                Button(
                    onClick = { onExport(group.groupKey) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_export_button_${group.groupKey.testTagSuffix()}"),
                ) {
                    Text(stringResource(R.string.history_export))
                }
            }
            SegmentCameraRow(
                title = context.getString(CameraDirection.Rear.labelRes()),
                segment = group.rearSegment,
                linked = group.frontSegment != null,
                onShare = onShare,
                onUnlock = onUnlock,
                onDelete = onDelete,
            )
            SegmentCameraRow(
                title = context.getString(CameraDirection.Front.labelRes()),
                segment = group.frontSegment,
                linked = group.rearSegment != null,
                onShare = onShare,
                onUnlock = onUnlock,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun SegmentCameraRow(
    title: String,
    segment: RecordingSegment?,
    linked: Boolean,
    onShare: (RecordingSegment) -> Unit,
    onUnlock: (RecordingSegment) -> Unit,
    onDelete: (RecordingSegment) -> Unit,
) {
    val context = LocalContext.current
    val statusText = when {
        segment == null -> context.getString(R.string.history_camera_missing)
        segment.locked -> context.getString(R.string.route_segment_status_locked)
        else -> context.getString(R.string.route_segment_status_normal)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.history_camera_status, title, statusText),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (segment?.locked == true) Color(0xFF9B2C2C) else Color(0xFF163036),
            )
            Text(
                text = if (segment != null) {
                    segment.sizeBytes.asFileSize()
                } else {
                    context.getString(R.string.history_camera_missing_short)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        }
        Text(
            text = when {
                segment != null && linked -> stringResource(R.string.history_camera_linked_file, segment.name)
                segment != null -> segment.name
                else -> stringResource(R.string.history_camera_missing_detail)
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4D6267),
        )
        segment?.let {
            Text(
                text = it.relativePath,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onShare(it) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.history_share))
                }
                if (it.locked) {
                    OutlinedButton(
                        onClick = { onUnlock(it) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.history_unlock))
                    }
                }
                OutlinedButton(
                    onClick = { onDelete(it) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.history_delete))
                }
            }
        }
    }
}

@Composable
private fun HistoryExportModeDialog(
    group: RecordingSegmentGroup,
    onDismiss: () -> Unit,
    onExport: (RecordingClipExportMode) -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        modifier = Modifier.testTag("history_export_dialog"),
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.history_export_dialog_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        R.string.history_group_export_title,
                        group.lastModifiedMillis.asTime(),
                        group.relationLabel(context),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF163036),
                )
                Text(
                    text = stringResource(R.string.history_export_dialog_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64777B),
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportModeButton(
                    label = stringResource(R.string.history_export_mode_rear_only),
                    enabled = group.rearSegment != null,
                    tag = "history_export_mode_rear",
                    onClick = { onExport(RecordingClipExportMode.RearOnly) },
                )
                ExportModeButton(
                    label = stringResource(R.string.history_export_mode_front_only),
                    enabled = group.frontSegment != null,
                    tag = "history_export_mode_front",
                    onClick = { onExport(RecordingClipExportMode.FrontOnly) },
                )
                ExportModeButton(
                    label = stringResource(R.string.history_export_mode_dual_package),
                    enabled = group.rearSegment != null && group.frontSegment != null,
                    tag = "history_export_mode_dual",
                    onClick = { onExport(RecordingClipExportMode.DualPackage) },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.events_collapse))
            }
        },
    )
}

@Composable
private fun ExportModeButton(
    label: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    ) {
        Text(label)
    }
}

@Composable
private fun SegmentFilterPanel(
    availableDays: List<String>,
    selectedDay: String?,
    selectedCameraFilter: SegmentCameraFilter,
    selectedLockFilter: SegmentLockFilter,
    onSelectedDayChanged: (String?) -> Unit,
    onCameraFilterChanged: (SegmentCameraFilter) -> Unit,
    onLockFilterChanged: (SegmentLockFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.history_filter_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        DayFilterRow(
            availableDays = availableDays,
            selectedDay = selectedDay,
            onSelectedDayChanged = onSelectedDayChanged,
        )
        SegmentCameraFilterRow(
            selected = selectedCameraFilter,
            onSelected = onCameraFilterChanged,
        )
        SegmentLockFilterRow(
            selected = selectedLockFilter,
            onSelected = onLockFilterChanged,
        )
    }
}

@Composable
private fun DayFilterRow(
    availableDays: List<String>,
    selectedDay: String?,
    onSelectedDayChanged: (String?) -> Unit,
) {
    val context = LocalContext.current
    val selectedIndex = selectedDay?.let { availableDays.indexOf(it) } ?: -1
    val canMovePrevious = availableDays.isNotEmpty() && (selectedIndex == -1 || selectedIndex < availableDays.lastIndex)
    val canMoveNext = availableDays.isNotEmpty() && selectedIndex > 0

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(
                R.string.history_selected_day,
                selectedDay ?: context.getString(R.string.history_all_days),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val nextIndex = if (selectedIndex == -1) 0 else (selectedIndex + 1).coerceAtMost(availableDays.lastIndex)
                    onSelectedDayChanged(availableDays.getOrNull(nextIndex))
                },
                enabled = canMovePrevious,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.history_previous_day))
            }
            Button(
                onClick = { onSelectedDayChanged(null) },
                enabled = selectedDay != null,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.label_segment_filter_all))
            }
            OutlinedButton(
                onClick = {
                    val nextIndex = (selectedIndex - 1).coerceAtLeast(0)
                    onSelectedDayChanged(availableDays.getOrNull(nextIndex))
                },
                enabled = canMoveNext,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.history_next_day))
            }
        }
    }
}

@Composable
private fun SegmentCameraFilterRow(
    selected: SegmentCameraFilter,
    onSelected: (SegmentCameraFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SegmentCameraFilter.entries.forEach { option ->
            FilterButton(
                label = stringResource(option.labelRes()),
                selected = selected == option,
                onClick = { onSelected(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SegmentLockFilterRow(
    selected: SegmentLockFilter,
    onSelected: (SegmentLockFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SegmentLockFilter.entries.forEach { option ->
            FilterButton(
                label = stringResource(option.labelRes()),
                selected = selected == option,
                onClick = { onSelected(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FilterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(label)
        }
    }
}

private data class RecordingSegmentGroup(
    val groupKey: String,
    val day: String,
    val lastModifiedMillis: Long,
    val rearSegment: RecordingSegment?,
    val frontSegment: RecordingSegment?,
) {
    val segmentCount: Int
        get() = listOfNotNull(rearSegment, frontSegment).size

    val totalSizeBytes: Long
        get() = listOfNotNull(rearSegment, frontSegment).sumOf { it.sizeBytes }

    fun relationLabel(context: android.content.Context): String {
        return when {
            rearSegment != null && frontSegment != null -> context.getString(R.string.history_group_relation_dual)
            rearSegment != null -> context.getString(R.string.history_group_relation_rear_only)
            frontSegment != null -> context.getString(R.string.history_group_relation_front_only)
            else -> context.getString(R.string.history_group_unknown)
        }
    }
}

private fun List<RecordingSegment>.toTimelineGroups(): List<RecordingSegmentGroup> {
    return groupBy { it.groupKey }
        .values
        .map { groupSegments ->
            RecordingSegmentGroup(
                groupKey = groupSegments.first().groupKey,
                day = groupSegments.first().day,
                lastModifiedMillis = groupSegments.maxOfOrNull { it.lastModifiedMillis } ?: 0L,
                rearSegment = groupSegments
                    .filter { it.cameraDirection == CameraDirection.Rear }
                    .maxByOrNull { it.lastModifiedMillis },
                frontSegment = groupSegments
                    .filter { it.cameraDirection == CameraDirection.Front }
                    .maxByOrNull { it.lastModifiedMillis },
            )
        }
        .sortedByDescending { it.lastModifiedMillis }
}

private fun Long.asTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))
}

private fun Long.asTimelineTime(): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(this))
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

private fun String.testTagSuffix(): String {
    return replace(Regex("[^A-Za-z0-9_]"), "_")
}
