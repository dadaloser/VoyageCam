package com.voyagecam.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.voyagecam.app.core.common.toContentUri
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.RecordingSegment
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

class ShareLauncher(
    private val context: Context,
    private val onStatus: (String) -> Unit,
) {
    fun shareSegment(segment: RecordingSegment) {
        runCatching {
            val uri = segment.toContentUri(context)
            val intent = Intent(Intent.ACTION_SEND)
                .setType(VIDEO_MIME_TYPE)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "分享录像片段"))
        }.onFailure { error ->
            onStatus("无法分享片段：${error.message ?: segment.name}")
        }
    }

    fun shareEmergencyEventFiles(event: EmergencyEvent, files: List<File>) {
        shareFiles(
            files = files,
            chooserTitle = "分享紧急事件片段",
            mimeType = VIDEO_MIME_TYPE,
            failurePrefix = "无法分享紧急事件",
            fallbackLabel = event.trigger.label,
        )
    }

    fun shareEvidencePackage(file: File) {
        runCatching {
            val uri = file.toContentUri(context)
            val intent = Intent(Intent.ACTION_SEND)
                .setType(ZIP_MIME_TYPE)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "分享证据包"))
        }.onFailure { error ->
            onStatus("无法分享证据包：${error.message ?: file.name}")
        }
    }

    fun openEmergencyEventMap(event: EmergencyEvent) {
        runCatching {
            val uri = event.toGeoUri() ?: error("该事件没有可用坐标")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        }.onFailure { error ->
            onStatus(
                if (error is ActivityNotFoundException) {
                    "未找到可打开坐标的地图应用。"
                } else {
                    "无法打开事件位置：${error.message ?: event.trigger.label}"
                },
            )
        }
    }

    fun openVideoFile(file: File) {
        runCatching {
            val uri = file.toContentUri(context)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, VIDEO_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        }.onFailure { error ->
            val message = if (error is ActivityNotFoundException) {
                "未找到可播放 MP4 的应用。"
            } else {
                "无法打开片段：${error.message ?: file.name}"
            }
            onStatus(message)
        }
    }

    private fun shareFiles(
        files: List<File>,
        chooserTitle: String,
        mimeType: String,
        failurePrefix: String,
        fallbackLabel: String,
    ) {
        runCatching {
            val uris = ArrayList<Uri>(files.map { it.toContentUri(context) })
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND)
                    .setType(mimeType)
                    .putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType(mimeType)
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            context.startActivity(Intent.createChooser(intent, chooserTitle))
        }.onFailure { error ->
            onStatus("$failurePrefix：${error.message ?: fallbackLabel}")
        }
    }
}

@Composable
fun rememberShareLauncher(
    context: Context,
    onStatus: (String) -> Unit,
): ShareLauncher {
    return remember(context, onStatus) {
        ShareLauncher(context, onStatus)
    }
}

private fun EmergencyEvent.toGeoUri(): Uri? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    val label = Uri.encode("VoyageCam ${trigger.label} ${triggeredAtMillis.asTime()}")
    return Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
}

private fun Long.asTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))
}

private const val VIDEO_MIME_TYPE = "video/mp4"
private const val ZIP_MIME_TYPE = "application/zip"
