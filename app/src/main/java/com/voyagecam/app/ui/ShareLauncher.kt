package com.voyagecam.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.voyagecam.app.R
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
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_segment_chooser)))
        }.onFailure { error ->
            onStatus(context.getString(R.string.share_segment_failed, error.message ?: segment.name))
        }
    }

    fun shareEmergencyEventFiles(event: EmergencyEvent, files: List<File>) {
        shareFiles(
            files = files,
            chooserTitle = context.getString(R.string.share_event_chooser),
            mimeType = VIDEO_MIME_TYPE,
            failureResId = R.string.share_event_failed,
            fallbackLabel = context.getString(event.trigger.labelRes()),
        )
    }

    fun shareEvidencePackage(file: File) {
        runCatching {
            val uri = file.toContentUri(context)
            val intent = Intent(Intent.ACTION_SEND)
                .setType(ZIP_MIME_TYPE)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_package_chooser)))
        }.onFailure { error ->
            onStatus(context.getString(R.string.share_package_failed, error.message ?: file.name))
        }
    }

    fun openEmergencyEventMap(event: EmergencyEvent) {
        runCatching {
            val uri = event.toGeoUri(context) ?: error(context.getString(R.string.share_event_no_location))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        }.onFailure { error ->
            onStatus(
                if (error is ActivityNotFoundException) {
                    context.getString(R.string.share_event_map_not_found)
                } else {
                    context.getString(
                        R.string.share_event_map_failed,
                        error.message ?: context.getString(event.trigger.labelRes()),
                    )
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
                context.getString(R.string.share_video_app_not_found)
            } else {
                context.getString(R.string.share_video_open_failed, error.message ?: file.name)
            }
            onStatus(message)
        }
    }

    private fun shareFiles(
        files: List<File>,
        chooserTitle: String,
        mimeType: String,
        failureResId: Int,
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
            onStatus(context.getString(failureResId, error.message ?: fallbackLabel))
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

private fun EmergencyEvent.toGeoUri(context: Context): Uri? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    val label = Uri.encode(
        context.getString(
            R.string.share_geo_label,
            context.getString(trigger.labelRes()),
            triggeredAtMillis.asTime(),
        ),
    )
    return Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
}

private fun Long.asTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))
}

private const val VIDEO_MIME_TYPE = "video/mp4"
private const val ZIP_MIME_TYPE = "application/zip"
