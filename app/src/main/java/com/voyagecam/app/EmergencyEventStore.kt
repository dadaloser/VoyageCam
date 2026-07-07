package com.voyagecam.app

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

class EmergencyEventStore(context: Context) {
    private val eventFile = File(context.filesDir, EVENT_FILE_NAME)

    @Synchronized
    fun createEvent(
        trigger: EmergencyTrigger,
        triggeredAtMillis: Long = System.currentTimeMillis(),
        accelerationG: Float? = null,
        thresholdG: Float? = null,
        location: EmergencyLocationSnapshot? = null,
    ): EmergencyEvent {
        val event = EmergencyEvent(
            id = "evt_${triggeredAtMillis}_${UUID.randomUUID().toString().take(8)}",
            trigger = trigger,
            triggeredAtMillis = triggeredAtMillis,
            accelerationG = accelerationG,
            thresholdG = thresholdG,
            latitude = location?.latitude,
            longitude = location?.longitude,
            speedMetersPerSecond = location?.speedMetersPerSecond,
            locationCapturedAtMillis = location?.capturedAtMillis,
            segmentPaths = emptyList(),
        )
        writeEvents((listOf(event) + listRecentEvents(MAX_EVENT_COUNT)).take(MAX_EVENT_COUNT))
        return event
    }

    @Synchronized
    fun addLockedSegment(eventId: String?, segmentPath: String?) {
        if (eventId.isNullOrBlank() || segmentPath.isNullOrBlank()) return

        val updated = listRecentEvents(MAX_EVENT_COUNT).map { event ->
            if (event.id != eventId) {
                event
            } else {
                event.copy(segmentPaths = (event.segmentPaths + segmentPath).distinct())
            }
        }
        writeEvents(updated)
    }

    @Synchronized
    fun listRecentEvents(limit: Int = DEFAULT_EVENT_LIST_LIMIT): List<EmergencyEvent> {
        if (!eventFile.exists()) return emptyList()
        return eventFile.readLines()
            .mapNotNull { line -> line.toEmergencyEventOrNull() }
            .sortedByDescending { it.triggeredAtMillis }
            .take(limit)
    }

    private fun writeEvents(events: List<EmergencyEvent>) {
        eventFile.parentFile?.mkdirs()
        eventFile.writeText(
            events
                .sortedByDescending { it.triggeredAtMillis }
                .take(MAX_EVENT_COUNT)
                .joinToString(separator = "\n") { it.toLine() },
        )
    }

    private fun EmergencyEvent.toLine(): String {
        return listOf(
            id,
            trigger.name,
            triggeredAtMillis.toString(),
            accelerationG?.toString().orEmpty(),
            thresholdG?.toString().orEmpty(),
            latitude?.toString().orEmpty(),
            longitude?.toString().orEmpty(),
            speedMetersPerSecond?.toString().orEmpty(),
            locationCapturedAtMillis?.toString().orEmpty(),
            segmentPaths.joinToString(separator = ",") { it.encodeField() },
        ).joinToString(separator = "\t")
    }

    private fun String.toEmergencyEventOrNull(): EmergencyEvent? {
        val parts = split('\t', limit = NEW_EVENT_FIELD_COUNT)
        if (parts.size < 6) return null
        val trigger = runCatching { EmergencyTrigger.valueOf(parts[TRIGGER_INDEX]) }.getOrNull() ?: return null
        val triggeredAtMillis = parts[TRIGGERED_AT_INDEX].toLongOrNull() ?: return null
        val hasLocationFields = parts.size >= NEW_EVENT_FIELD_COUNT
        val segmentField = if (hasLocationFields) parts[SEGMENT_PATHS_INDEX] else parts[OLD_SEGMENT_PATHS_INDEX]
        return EmergencyEvent(
            id = parts[ID_INDEX],
            trigger = trigger,
            triggeredAtMillis = triggeredAtMillis,
            accelerationG = parts[ACCELERATION_INDEX].toFloatOrNull(),
            thresholdG = parts[THRESHOLD_INDEX].toFloatOrNull(),
            latitude = if (hasLocationFields) parts[LATITUDE_INDEX].toDoubleOrNull() else null,
            longitude = if (hasLocationFields) parts[LONGITUDE_INDEX].toDoubleOrNull() else null,
            speedMetersPerSecond = if (hasLocationFields) parts[SPEED_INDEX].toFloatOrNull() else null,
            locationCapturedAtMillis = if (hasLocationFields) parts[LOCATION_CAPTURED_AT_INDEX].toLongOrNull() else null,
            segmentPaths = segmentField
                .takeIf { it.isNotBlank() }
                ?.split(',')
                ?.mapNotNull { it.decodeFieldOrNull() }
                .orEmpty(),
        )
    }

    private fun String.encodeField(): String {
        val bytes = toByteArray(StandardCharsets.UTF_8)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun String.decodeFieldOrNull(): String? {
        return runCatching {
            val bytes = Base64.getUrlDecoder().decode(this)
            String(bytes, StandardCharsets.UTF_8)
        }.getOrNull()
    }

    companion object {
        private const val EVENT_FILE_NAME = "emergency_events.tsv"
        private const val DEFAULT_EVENT_LIST_LIMIT = 10
        private const val MAX_EVENT_COUNT = 100
        private const val ID_INDEX = 0
        private const val TRIGGER_INDEX = 1
        private const val TRIGGERED_AT_INDEX = 2
        private const val ACCELERATION_INDEX = 3
        private const val THRESHOLD_INDEX = 4
        private const val OLD_SEGMENT_PATHS_INDEX = 5
        private const val LATITUDE_INDEX = 5
        private const val LONGITUDE_INDEX = 6
        private const val SPEED_INDEX = 7
        private const val LOCATION_CAPTURED_AT_INDEX = 8
        private const val SEGMENT_PATHS_INDEX = 9
        private const val NEW_EVENT_FIELD_COUNT = 10
    }
}

data class EmergencyEvent(
    val id: String,
    val trigger: EmergencyTrigger,
    val triggeredAtMillis: Long,
    val accelerationG: Float?,
    val thresholdG: Float?,
    val latitude: Double?,
    val longitude: Double?,
    val speedMetersPerSecond: Float?,
    val locationCapturedAtMillis: Long?,
    val segmentPaths: List<String>,
)

enum class EmergencyTrigger(val label: String) {
    Manual("手动锁定"),
    Collision("碰撞触发"),
}
