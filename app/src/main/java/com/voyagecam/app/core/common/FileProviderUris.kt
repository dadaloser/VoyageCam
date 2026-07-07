package com.voyagecam.app.core.common

import android.content.Context
import androidx.core.content.FileProvider
import com.voyagecam.app.core.model.RecordingSegment
import java.io.File

fun RecordingSegment.toContentUri(context: Context) =
    FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        File(absolutePath),
    )

fun File.toContentUri(context: Context) =
    FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        this,
    )
