package com.voyagecam.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.core.model.RecordingSegment
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
    onUnlock: (RecordingSegment) -> Unit,
    onDelete: (RecordingSegment) -> Unit,
) {
    val context = LocalContext.current
    val groupedDirections = allSegments
        .groupBy { it.groupKey }
        .mapValues { entry -> entry.value.map { it.cameraDirection }.toSet() }

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
                text = stringResource(R.string.history_summary, segments.size, totalSegmentCount),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
            Spacer(modifier = Modifier.height(12.dp))
            segments.forEachIndexed { index, segment ->
                RecordingSegmentRow(
                    segment = segment,
                    groupedDirections = groupedDirections[segment.groupKey].orEmpty(),
                    onOpen = onOpen,
                    onShare = onShare,
                    onUnlock = onUnlock,
                    onDelete = onDelete,
                )
                if (index != segments.lastIndex) {
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

@Composable
private fun RecordingSegmentRow(
    segment: RecordingSegment,
    groupedDirections: Set<CameraDirection>,
    onOpen: (RecordingSegment) -> Unit,
    onShare: (RecordingSegment) -> Unit,
    onUnlock: (RecordingSegment) -> Unit,
    onDelete: (RecordingSegment) -> Unit,
) {
    val context = LocalContext.current
    val stateLabel = context.getString(
        if (segment.locked) {
            R.string.route_segment_status_locked
        } else {
            R.string.route_segment_status_normal
        },
    )
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
                text = "${context.getString(segment.cameraDirection.labelRes())} · $stateLabel",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (segment.locked) Color(0xFF9B2C2C) else Color(0xFF163036),
            )
            Text(
                text = segment.sizeBytes.asFileSize(),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        }
        Text(
            text = segment.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4D6267),
        )
        Text(
            text = "${segment.lastModifiedMillis.asTime()} · ${segment.relativePath}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
        Text(
            text = stringResource(R.string.history_grouped, groupedDirections.asDirectionSummary(context)),
            style = MaterialTheme.typography.bodySmall,
            color = if (groupedDirections.containsAll(setOf(CameraDirection.Rear, CameraDirection.Front))) {
                Color(0xFF2F6F62)
            } else {
                Color(0xFF64777B)
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onOpen(segment) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.history_open))
            }
            OutlinedButton(
                onClick = { onShare(segment) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.history_share))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (segment.locked) {
                OutlinedButton(
                    onClick = { onUnlock(segment) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.history_unlock))
                }
            }
            OutlinedButton(
                onClick = { onDelete(segment) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.history_delete))
            }
        }
    }
}

private fun Set<CameraDirection>.asDirectionSummary(context: android.content.Context): String {
    if (isEmpty()) return context.getString(R.string.history_group_unknown)
    return buildList {
        if (contains(CameraDirection.Rear)) add(context.getString(CameraDirection.Rear.labelRes()))
        if (contains(CameraDirection.Front)) add(context.getString(CameraDirection.Front.labelRes()))
    }.joinToString(" + ")
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
