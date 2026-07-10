package com.voyagecam.app.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voyagecam.app.R
import com.voyagecam.app.ui.events.EvidenceExportState
import java.io.File
import java.util.Locale

@Composable
fun ExportStatusPanel(
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
                    text = stringResource(R.string.export_status_running),
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
                        Text(stringResource(R.string.export_status_cancel))
                    }
                }
            }

            is EvidenceExportState.Ready -> {
                Text(
                    text = stringResource(R.string.export_status_ready),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF163036),
                )
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF163036),
                )
                Text(
                    text = stringResource(
                        R.string.export_status_ready_summary,
                        state.file.name,
                        state.itemCount,
                        state.file.length().asFileSize(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4D6267),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onShare(state.file) }) {
                        Text(stringResource(R.string.export_status_share))
                    }
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringResource(R.string.events_collapse))
                    }
                }
            }

            is EvidenceExportState.Failed -> {
                Text(
                    text = stringResource(R.string.export_status_failed),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF9B2C2C),
                )
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF163036),
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4D6267),
                )
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.events_collapse))
                }
            }

            is EvidenceExportState.Cancelled -> {
                Text(
                    text = stringResource(R.string.export_status_cancelled),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF163036),
                )
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF163036),
                )
                if (state.message.isNotBlank()) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4D6267),
                    )
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.events_collapse))
                }
            }
        }
    }
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
