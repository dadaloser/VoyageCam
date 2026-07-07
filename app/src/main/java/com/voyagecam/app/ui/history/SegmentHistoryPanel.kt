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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voyagecam.app.core.model.RecordingSegment
import com.voyagecam.app.ui.theme.SectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SegmentHistoryPanel(
    segments: List<RecordingSegment>,
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
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "录像片段",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF163036),
            )
            OutlinedButton(onClick = onRefresh) {
                Text("刷新")
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
                text = "暂无片段。开始录制后，这里会显示最近生成的普通和锁定视频。",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        } else if (segments.isEmpty()) {
            Text(
                text = "当前筛选条件下没有片段。",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        } else {
            Text(
                text = "显示 ${segments.size}/${totalSegmentCount} 个片段 · 锁定片段不会被循环清理",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
            Spacer(modifier = Modifier.height(12.dp))
            segments.forEachIndexed { index, segment ->
                RecordingSegmentRow(
                    segment = segment,
                    onOpen = onOpen,
                    onShare = onShare,
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
            text = "筛选",
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
    val selectedIndex = selectedDay?.let { availableDays.indexOf(it) } ?: -1
    val canMovePrevious = availableDays.isNotEmpty() && (selectedIndex == -1 || selectedIndex < availableDays.lastIndex)
    val canMoveNext = availableDays.isNotEmpty() && selectedIndex > 0

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "日期：${selectedDay ?: "全部日期"}",
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
                Text("上一天")
            }
            Button(
                onClick = { onSelectedDayChanged(null) },
                enabled = selectedDay != null,
                modifier = Modifier.weight(1f),
            ) {
                Text("全部")
            }
            OutlinedButton(
                onClick = {
                    val nextIndex = (selectedIndex - 1).coerceAtLeast(0)
                    onSelectedDayChanged(availableDays.getOrNull(nextIndex))
                },
                enabled = canMoveNext,
                modifier = Modifier.weight(1f),
            ) {
                Text("下一天")
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
                label = option.label,
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
                label = option.label,
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
    onOpen: (RecordingSegment) -> Unit,
    onShare: (RecordingSegment) -> Unit,
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
                text = "${segment.cameraDirection.label} · ${if (segment.locked) "已锁定" else "普通"}",
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onOpen(segment) },
                modifier = Modifier.weight(1f),
            ) {
                Text("播放")
            }
            OutlinedButton(
                onClick = { onShare(segment) },
                modifier = Modifier.weight(1f),
            ) {
                Text("分享")
            }
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
