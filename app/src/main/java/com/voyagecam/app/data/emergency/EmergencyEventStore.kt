package com.voyagecam.app.data.emergency

import android.content.Context
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.EmergencyEventRepairResult
import com.voyagecam.app.core.model.EmergencyLocationSnapshot
import com.voyagecam.app.core.model.EmergencyTrigger
import com.voyagecam.app.core.model.GpsTrackPoint
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
        gpsTrackPoints: List<GpsTrackPoint> = emptyList(),
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
            bearingDegrees = location?.bearingDegrees,
            locationCapturedAtMillis = location?.capturedAtMillis,
            segmentPaths = emptyList(),
            gpsTrackPoints = gpsTrackPoints,
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
    fun removeSegment(segmentPath: String?) {
        if (segmentPath.isNullOrBlank()) return

        val updated = listRecentEvents(MAX_EVENT_COUNT).map { event ->
            event.copy(segmentPaths = event.segmentPaths.filterNot { it == segmentPath })
        }
        writeEvents(updated)
    }

    @Synchronized
    fun deleteEvent(eventId: String?) {
        if (eventId.isNullOrBlank()) return

        val updated = listRecentEvents(MAX_EVENT_COUNT).filterNot { it.id == eventId }
        writeEvents(updated)
    }

    @Synchronized
    fun repairMissingSegments(segmentExists: (String) -> Boolean): EmergencyEventRepairResult {
        var updatedEvents = 0
        var removedSegmentPaths = 0
        var emptyEvents = 0

        val updated = listRecentEvents(MAX_EVENT_COUNT).map { event ->
            val repairedPaths = event.segmentPaths.filter(segmentExists)
            if (repairedPaths.size != event.segmentPaths.size) {
                updatedEvents++
                removedSegmentPaths += event.segmentPaths.size - repairedPaths.size
            }
            if (repairedPaths.isEmpty()) {
                emptyEvents++
            }
            event.copy(segmentPaths = repairedPaths)
        }

        writeEvents(updated)
        return EmergencyEventRepairResult(
            updatedEvents = updatedEvents,
            removedSegmentPaths = removedSegmentPaths,
            emptyEvents = emptyEvents,
        )
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
            bearingDegrees?.toString().orEmpty(),
            locationCapturedAtMillis?.toString().orEmpty(),
            segmentPaths.joinToString(separator = ",") { it.encodeField() },
            gpsTrackPoints.toTrackField(),
        ).joinToString(separator = "\t")
    }

    private fun String.toEmergencyEventOrNull(): EmergencyEvent? {
        val parts = split('\t', limit = EVENT_FIELD_COUNT)
        if (parts.size < 6) return null
        val trigger = runCatching { EmergencyTrigger.valueOf(parts[TRIGGER_INDEX]) }.getOrNull() ?: return null
        val triggeredAtMillis = parts[TRIGGERED_AT_INDEX].toLongOrNull() ?: return null
        val hasBearingFields = parts.size >= BEARING_EVENT_FIELD_COUNT
        val hasLocationFields = hasBearingFields || parts.size >= LOCATION_EVENT_FIELD_COUNT
        val segmentField = when {
            hasBearingFields -> parts[BEARING_SEGMENT_PATHS_INDEX]
            hasLocationFields -> parts[SEGMENT_PATHS_INDEX]
            else -> parts[OLD_SEGMENT_PATHS_INDEX]
        }
        val trackField = when {
            hasBearingFields -> parts.getOrNull(BEARING_GPS_TRACK_INDEX)
            else -> parts.getOrNull(GPS_TRACK_INDEX)
        }
        return EmergencyEvent(
            id = parts[ID_INDEX],
            trigger = trigger,
            triggeredAtMillis = triggeredAtMillis,
            accelerationG = parts[ACCELERATION_INDEX].toFloatOrNull(),
            thresholdG = parts[THRESHOLD_INDEX].toFloatOrNull(),
            latitude = if (hasLocationFields) parts[LATITUDE_INDEX].toDoubleOrNull() else null,
            longitude = if (hasLocationFields) parts[LONGITUDE_INDEX].toDoubleOrNull() else null,
            speedMetersPerSecond = if (hasLocationFields) parts[SPEED_INDEX].toFloatOrNull() else null,
            bearingDegrees = if (hasBearingFields) parts[BEARING_INDEX].toFloatOrNull() else null,
            locationCapturedAtMillis = if (hasBearingFields) {
                parts[BEARING_LOCATION_CAPTURED_AT_INDEX].toLongOrNull()
            } else if (hasLocationFields) {
                parts[LOCATION_CAPTURED_AT_INDEX].toLongOrNull()
            } else {
                null
            },
            segmentPaths = segmentField
                .takeIf { it.isNotBlank() }
                ?.split(',')
                ?.mapNotNull { it.decodeFieldOrNull() }
                .orEmpty(),
            gpsTrackPoints = trackField.toGpsTrackPoints(),
        )
    }

    private fun List<GpsTrackPoint>.toTrackField(): String {
        if (isEmpty()) return ""
        val encodedTrack = joinToString(separator = ";") { point ->
            listOf(
                point.capturedAtMillis.toString(),
                point.latitude.toString(),
                point.longitude.toString(),
                point.speedMetersPerSecond?.toString().orEmpty(),
                point.bearingDegrees?.toString().orEmpty(),
            ).joinToString(separator = ",")
        }
        return encodedTrack.encodeField()
    }

    private fun String?.toGpsTrackPoints(): List<GpsTrackPoint> {
        if (isNullOrBlank()) return emptyList()
        val decoded = decodeFieldOrNull().orEmpty()
        if (decoded.isBlank()) return emptyList()
        return decoded
            .split(';')
            .mapNotNull { entry ->
                val parts = entry.split(',', limit = BEARING_GPS_TRACK_PART_COUNT)
                val capturedAt = parts.getOrNull(GPS_TRACK_TIME_INDEX)?.toLongOrNull() ?: return@mapNotNull null
                val latitude = parts.getOrNull(GPS_TRACK_LATITUDE_INDEX)?.toDoubleOrNull() ?: return@mapNotNull null
                val longitude = parts.getOrNull(GPS_TRACK_LONGITUDE_INDEX)?.toDoubleOrNull() ?: return@mapNotNull null
                GpsTrackPoint(
                    latitude = latitude,
                    longitude = longitude,
                    speedMetersPerSecond = parts.getOrNull(GPS_TRACK_SPEED_INDEX)?.toFloatOrNull(),
                    bearingDegrees = parts.getOrNull(GPS_TRACK_BEARING_INDEX)?.toFloatOrNull(),
                    capturedAtMillis = capturedAt,
                )
            }
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
        private const val GPS_TRACK_INDEX = 10
        private const val BEARING_INDEX = 8
        private const val BEARING_LOCATION_CAPTURED_AT_INDEX = 9
        private const val BEARING_SEGMENT_PATHS_INDEX = 10
        private const val BEARING_GPS_TRACK_INDEX = 11
        private const val LOCATION_EVENT_FIELD_COUNT = 10
        private const val EVENT_FIELD_COUNT = 12
        private const val BEARING_EVENT_FIELD_COUNT = 12
        private const val BEARING_GPS_TRACK_PART_COUNT = 5
        private const val GPS_TRACK_TIME_INDEX = 0
        private const val GPS_TRACK_LATITUDE_INDEX = 1
        private const val GPS_TRACK_LONGITUDE_INDEX = 2
        private const val GPS_TRACK_SPEED_INDEX = 3
        private const val GPS_TRACK_BEARING_INDEX = 4
    }
}
