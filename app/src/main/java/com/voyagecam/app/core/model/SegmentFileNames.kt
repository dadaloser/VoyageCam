package com.voyagecam.app.core.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SegmentFileNames(
    val day: String,
    val group: String,
    val filePrefix: String,
) {
    companion object {
        private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val groupFormat = SimpleDateFormat("'group_'HHmmss", Locale.US)
        private val fileFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

        fun from(startedAtMillis: Long): SegmentFileNames {
            val date = Date(startedAtMillis)
            return SegmentFileNames(
                day = dayFormat.format(date),
                group = groupFormat.format(date),
                filePrefix = fileFormat.format(date),
            )
        }
    }
}
