package com.voyagecam.app.data.emergency

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.EmergencyEventRepairResult
import com.voyagecam.app.core.model.EmergencyLocationSnapshot
import com.voyagecam.app.core.model.EmergencyTrigger
import com.voyagecam.app.core.model.GpsTrackPoint
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EmergencyEventStore(context: Context) {
    private val eventFile = File(context.filesDir, EVENT_FILE_NAME)
    private val database = EmergencyEventDatabase.from(context.applicationContext)
    private val dao = database.emergencyEventDao()
    private val legacyImportMutex = Mutex()
    private var legacyImportChecked = false
    private var legacyRelationImportChecked = false

    suspend fun createEvent(
        trigger: EmergencyTrigger,
        triggeredAtMillis: Long = System.currentTimeMillis(),
        accelerationG: Float? = null,
        thresholdG: Float? = null,
        location: EmergencyLocationSnapshot? = null,
        gpsTrackPoints: List<GpsTrackPoint> = emptyList(),
    ): EmergencyEvent {
        ensureLegacyFileImported()
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
        dao.insertEvent(event.toEntity())
        dao.replaceSegments(event.id, event.segmentPaths.mapIndexed { index, path -> event.toSegmentEntity(index, path) })
        dao.replaceTrackPoints(event.id, event.gpsTrackPoints.mapIndexed { index, point -> event.toTrackPointEntity(index, point) })
        dao.pruneToMostRecent(MAX_EVENT_COUNT)
        dao.pruneSegmentsToMostRecent(MAX_EVENT_COUNT)
        dao.pruneTrackPointsToMostRecent(MAX_EVENT_COUNT)
        return event
    }

    suspend fun addLockedSegment(eventId: String?, segmentPath: String?) {
        if (eventId.isNullOrBlank() || segmentPath.isNullOrBlank()) return

        ensureLegacyFileImported()
        dao.findById(eventId) ?: return
        val existing = dao.listSegments(eventId).map { it.segmentPath }
        if (segmentPath !in existing) {
            dao.insertSegment(
                EmergencyEventSegmentEntity(
                    id = "$eventId:${segmentPath.encodeField()}",
                    eventId = eventId,
                    segmentPath = segmentPath,
                    sortOrder = existing.size,
                ),
            )
        }
    }

    suspend fun removeSegment(segmentPath: String?) {
        if (segmentPath.isNullOrBlank()) return

        ensureLegacyFileImported()
        dao.deleteSegmentPath(segmentPath)
    }

    suspend fun deleteEvent(eventId: String?) {
        if (eventId.isNullOrBlank()) return

        ensureLegacyFileImported()
        dao.deleteSegmentsByEventId(eventId)
        dao.deleteTrackPointsByEventId(eventId)
        dao.deleteById(eventId)
    }

    suspend fun repairMissingSegments(segmentExists: (String) -> Boolean): EmergencyEventRepairResult {
        ensureLegacyFileImported()
        var updatedEvents = 0
        var removedSegmentPaths = 0
        var emptyEvents = 0

        dao.listRecent(MAX_EVENT_COUNT)
            .map { it.toEmergencyEvent() }
            .map { event ->
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
            .forEach { event ->
                dao.replaceSegments(
                    eventId = event.id,
                    segments = event.segmentPaths.mapIndexed { index, path -> event.toSegmentEntity(index, path) },
                )
            }

        return EmergencyEventRepairResult(
            updatedEvents = updatedEvents,
            removedSegmentPaths = removedSegmentPaths,
            emptyEvents = emptyEvents,
        )
    }

    suspend fun listRecentEvents(limit: Int = DEFAULT_EVENT_LIST_LIMIT): List<EmergencyEvent> {
        ensureLegacyFileImported()
        return dao.listRecent(limit).map { it.toEmergencyEvent() }
    }

    private suspend fun ensureLegacyFileImported() {
        if (legacyImportChecked) return

        legacyImportMutex.withLock {
            if (legacyImportChecked) return

            if (!eventFile.exists() || dao.count() > 0) {
                ensureLegacyRelationsImported()
                legacyImportChecked = true
                return
            }

            val legacyEvents = withContext(Dispatchers.IO) { eventFile.readLines() }
                .mapNotNull { line -> line.toEmergencyEventOrNull() }
                .sortedByDescending { it.triggeredAtMillis }
                .take(MAX_EVENT_COUNT)
            if (legacyEvents.isNotEmpty()) {
                dao.insertEvents(legacyEvents.map { it.toEntity() })
                legacyEvents.forEach { event ->
                    dao.replaceSegments(
                        event.id,
                        event.segmentPaths.mapIndexed { index, path -> event.toSegmentEntity(index, path) },
                    )
                    dao.replaceTrackPoints(
                        event.id,
                        event.gpsTrackPoints.mapIndexed { index, point -> event.toTrackPointEntity(index, point) },
                    )
                }
                dao.pruneToMostRecent(MAX_EVENT_COUNT)
                dao.pruneSegmentsToMostRecent(MAX_EVENT_COUNT)
                dao.pruneTrackPointsToMostRecent(MAX_EVENT_COUNT)
            }
            ensureLegacyRelationsImported()
            legacyImportChecked = true
        }
    }

    private suspend fun ensureLegacyRelationsImported() {
        if (legacyRelationImportChecked) return
        if (dao.segmentCount() > 0 || dao.trackPointCount() > 0) {
            legacyRelationImportChecked = true
            return
        }

        dao.listRecent(MAX_EVENT_COUNT).forEach { entity ->
            val event = entity.toEmergencyEventFromLegacyFields()
            dao.replaceSegments(
                event.id,
                event.segmentPaths.mapIndexed { index, path -> event.toSegmentEntity(index, path) },
            )
            dao.replaceTrackPoints(
                event.id,
                event.gpsTrackPoints.mapIndexed { index, point -> event.toTrackPointEntity(index, point) },
            )
        }
        legacyRelationImportChecked = true
    }

    private fun EmergencyEvent.toEntity(): EmergencyEventEntity {
        return EmergencyEventEntity(
            id = id,
            trigger = trigger.name,
            triggeredAtMillis = triggeredAtMillis,
            accelerationG = accelerationG,
            thresholdG = thresholdG,
            latitude = latitude,
            longitude = longitude,
            speedMetersPerSecond = speedMetersPerSecond,
            bearingDegrees = bearingDegrees,
            locationCapturedAtMillis = locationCapturedAtMillis,
            legacySegmentPaths = segmentPaths.toSegmentPathField(),
            legacyGpsTrackPoints = gpsTrackPoints.toTrackField(),
        )
    }

    private suspend fun EmergencyEventEntity.toEmergencyEvent(): EmergencyEvent {
        val segments = dao.listSegments(id).map { it.segmentPath }
        val trackPoints = dao.listTrackPoints(id).map { it.toGpsTrackPoint() }
        return EmergencyEvent(
            id = id,
            trigger = runCatching { EmergencyTrigger.valueOf(trigger) }.getOrDefault(EmergencyTrigger.Manual),
            triggeredAtMillis = triggeredAtMillis,
            accelerationG = accelerationG,
            thresholdG = thresholdG,
            latitude = latitude,
            longitude = longitude,
            speedMetersPerSecond = speedMetersPerSecond,
            bearingDegrees = bearingDegrees,
            locationCapturedAtMillis = locationCapturedAtMillis,
            segmentPaths = segments,
            gpsTrackPoints = trackPoints,
        )
    }

    private fun EmergencyEventEntity.toEmergencyEventFromLegacyFields(): EmergencyEvent {
        @Suppress("DEPRECATION")
        return EmergencyEvent(
            id = id,
            trigger = runCatching { EmergencyTrigger.valueOf(trigger) }.getOrDefault(EmergencyTrigger.Manual),
            triggeredAtMillis = triggeredAtMillis,
            accelerationG = accelerationG,
            thresholdG = thresholdG,
            latitude = latitude,
            longitude = longitude,
            speedMetersPerSecond = speedMetersPerSecond,
            bearingDegrees = bearingDegrees,
            locationCapturedAtMillis = locationCapturedAtMillis,
            segmentPaths = legacySegmentPaths.toSegmentPaths(),
            gpsTrackPoints = legacyGpsTrackPoints.toGpsTrackPoints(),
        )
    }

    private fun EmergencyEvent.toSegmentEntity(index: Int, segmentPath: String): EmergencyEventSegmentEntity {
        return EmergencyEventSegmentEntity(
            id = "$id:${segmentPath.encodeField()}",
            eventId = id,
            segmentPath = segmentPath,
            sortOrder = index,
        )
    }

    private fun EmergencyEvent.toTrackPointEntity(index: Int, point: GpsTrackPoint): EmergencyGpsTrackPointEntity {
        return EmergencyGpsTrackPointEntity(
            id = "$id:${index}_${point.capturedAtMillis}",
            eventId = id,
            sortOrder = index,
            capturedAtMillis = point.capturedAtMillis,
            latitude = point.latitude,
            longitude = point.longitude,
            speedMetersPerSecond = point.speedMetersPerSecond,
            bearingDegrees = point.bearingDegrees,
        )
    }

    private fun EmergencyGpsTrackPointEntity.toGpsTrackPoint(): GpsTrackPoint {
        return GpsTrackPoint(
            latitude = latitude,
            longitude = longitude,
            speedMetersPerSecond = speedMetersPerSecond,
            bearingDegrees = bearingDegrees,
            capturedAtMillis = capturedAtMillis,
        )
    }

    private fun List<String>.toSegmentPathField(): String {
        return joinToString(separator = ",") { it.encodeField() }
    }

    private fun String.toSegmentPaths(): List<String> {
        return takeIf { it.isNotBlank() }
            ?.split(',')
            ?.mapNotNull { it.decodeFieldOrNull() }
            .orEmpty()
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
            segmentPaths = segmentField.toSegmentPaths(),
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

@Entity(tableName = "emergency_events")
data class EmergencyEventEntity(
    @PrimaryKey val id: String,
    val trigger: String,
    val triggeredAtMillis: Long,
    val accelerationG: Float?,
    val thresholdG: Float?,
    val latitude: Double?,
    val longitude: Double?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val locationCapturedAtMillis: Long?,
    @Deprecated("Use emergency_event_segments")
    val legacySegmentPaths: String,
    @Deprecated("Use emergency_gps_track_points")
    val legacyGpsTrackPoints: String,
)

@Entity(
    tableName = "emergency_event_segments",
    indices = [
        Index(value = ["eventId"]),
        Index(value = ["segmentPath"], unique = true),
    ],
)
data class EmergencyEventSegmentEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val segmentPath: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "emergency_gps_track_points",
    indices = [Index(value = ["eventId"])],
)
data class EmergencyGpsTrackPointEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val sortOrder: Int,
    val capturedAtMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
)

@Dao
interface EmergencyEventDao {
    @Query("SELECT COUNT(*) FROM emergency_events")
    suspend fun count(): Int

    @Query("SELECT * FROM emergency_events ORDER BY triggeredAtMillis DESC LIMIT :limit")
    suspend fun listRecent(limit: Int): List<EmergencyEventEntity>

    @Query("SELECT * FROM emergency_events WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): EmergencyEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EmergencyEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EmergencyEventEntity>)

    @Query("DELETE FROM emergency_events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM emergency_event_segments WHERE eventId = :eventId ORDER BY sortOrder ASC")
    suspend fun listSegments(eventId: String): List<EmergencyEventSegmentEntity>

    @Query("SELECT COUNT(*) FROM emergency_event_segments")
    suspend fun segmentCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: EmergencyEventSegmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<EmergencyEventSegmentEntity>)

    @Query("DELETE FROM emergency_event_segments WHERE eventId = :eventId")
    suspend fun deleteSegmentsByEventId(eventId: String)

    @Query("DELETE FROM emergency_event_segments WHERE segmentPath = :segmentPath")
    suspend fun deleteSegmentPath(segmentPath: String)

    suspend fun replaceSegments(eventId: String, segments: List<EmergencyEventSegmentEntity>) {
        deleteSegmentsByEventId(eventId)
        if (segments.isNotEmpty()) insertSegments(segments)
    }

    @Query("SELECT * FROM emergency_gps_track_points WHERE eventId = :eventId ORDER BY sortOrder ASC")
    suspend fun listTrackPoints(eventId: String): List<EmergencyGpsTrackPointEntity>

    @Query("SELECT COUNT(*) FROM emergency_gps_track_points")
    suspend fun trackPointCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPoints(points: List<EmergencyGpsTrackPointEntity>)

    @Query("DELETE FROM emergency_gps_track_points WHERE eventId = :eventId")
    suspend fun deleteTrackPointsByEventId(eventId: String)

    suspend fun replaceTrackPoints(eventId: String, points: List<EmergencyGpsTrackPointEntity>) {
        deleteTrackPointsByEventId(eventId)
        if (points.isNotEmpty()) insertTrackPoints(points)
    }

    @Query(
        """
        DELETE FROM emergency_events
        WHERE id NOT IN (
            SELECT id FROM emergency_events ORDER BY triggeredAtMillis DESC LIMIT :maxCount
        )
        """,
    )
    suspend fun pruneToMostRecent(maxCount: Int)

    @Query(
        """
        DELETE FROM emergency_event_segments
        WHERE eventId NOT IN (
            SELECT id FROM emergency_events ORDER BY triggeredAtMillis DESC LIMIT :maxCount
        )
        """,
    )
    suspend fun pruneSegmentsToMostRecent(maxCount: Int)

    @Query(
        """
        DELETE FROM emergency_gps_track_points
        WHERE eventId NOT IN (
            SELECT id FROM emergency_events ORDER BY triggeredAtMillis DESC LIMIT :maxCount
        )
        """,
    )
    suspend fun pruneTrackPointsToMostRecent(maxCount: Int)
}

@Database(
    entities = [
        EmergencyEventEntity::class,
        EmergencyEventSegmentEntity::class,
        EmergencyGpsTrackPointEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class EmergencyEventDatabase : RoomDatabase() {
    abstract fun emergencyEventDao(): EmergencyEventDao

    companion object {
        @Volatile
        private var instance: EmergencyEventDatabase? = null

        fun from(context: Context): EmergencyEventDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EmergencyEventDatabase::class.java,
                    "voyagecam_emergency_events.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `emergency_event_segments` (
                        `id` TEXT NOT NULL,
                        `eventId` TEXT NOT NULL,
                        `segmentPath` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """,
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_emergency_event_segments_eventId` ON `emergency_event_segments` (`eventId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_emergency_event_segments_segmentPath` ON `emergency_event_segments` (`segmentPath`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `emergency_gps_track_points` (
                        `id` TEXT NOT NULL,
                        `eventId` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `capturedAtMillis` INTEGER NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `speedMetersPerSecond` REAL,
                        `bearingDegrees` REAL,
                        PRIMARY KEY(`id`)
                    )
                    """,
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_emergency_gps_track_points_eventId` ON `emergency_gps_track_points` (`eventId`)")
            }
        }
    }
}
