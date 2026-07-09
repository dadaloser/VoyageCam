package com.voyagecam.app.data.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.voyagecam.app.R
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.core.model.RecordingSegment
import java.io.File

class RecordingSegmentIndexStore(context: Context) {
    private val prefs = context.getSharedPreferences("voyage_cam_recording_segment_index", Context.MODE_PRIVATE)
    private val database = RecordingSegmentIndexDatabase.from(context.applicationContext)
    private val dao = database.recordingSegmentIndexDao()
    private val unknownDateLabel = context.applicationContext.getString(R.string.common_unknown_date)

    suspend fun ensureImported(normalRoot: File, lockedRoot: File, dashcamRoot: File) {
        if (prefs.getBoolean(KEY_IMPORTED, false)) return

        val segments = buildList {
            addAll(scanSegments(normalRoot, dashcamRoot, locked = false))
            addAll(scanSegments(lockedRoot, dashcamRoot, locked = true))
        }
        dao.replaceAll(segments)
        prefs.edit().putBoolean(KEY_IMPORTED, true).apply()
    }

    suspend fun rebuild(normalRoot: File, lockedRoot: File, dashcamRoot: File) {
        val segments = buildList {
            addAll(scanSegments(normalRoot, dashcamRoot, locked = false))
            addAll(scanSegments(lockedRoot, dashcamRoot, locked = true))
        }
        dao.replaceAll(segments)
        prefs.edit().putBoolean(KEY_IMPORTED, true).apply()
    }

    suspend fun listRecentSegments(
        normalRoot: File,
        lockedRoot: File,
        dashcamRoot: File,
        limit: Int,
    ): List<RecordingSegment> {
        ensureImported(normalRoot, lockedRoot, dashcamRoot)
        return dao.listRecent(limit * 2)
            .mapNotNull { entity -> entity.toRecordingSegmentOrNull(dashcamRoot, removeStale = true) }
            .take(limit)
    }

    suspend fun normalSegmentEntities(normalRoot: File, lockedRoot: File, dashcamRoot: File): List<RecordingSegmentIndexEntity> {
        ensureImported(normalRoot, lockedRoot, dashcamRoot)
        return dao.listNormal()
    }

    suspend fun storageSnapshot(normalRoot: File, lockedRoot: File, dashcamRoot: File): RecordingSegmentStorageSnapshot {
        ensureImported(normalRoot, lockedRoot, dashcamRoot)
        val entities = dao.listAll()
        val validEntities = mutableListOf<RecordingSegmentIndexEntity>()
        entities.forEach { entity ->
            val file = File(dashcamRoot, entity.dashcamPath)
            if (file.exists() && file.isFile) {
                val fresh = entity.withFreshFileStats(file)
                validEntities += fresh
                if (fresh != entity) dao.insert(fresh)
            } else {
                dao.deleteByPath(entity.dashcamPath)
            }
        }

        return RecordingSegmentStorageSnapshot(
            normalBytes = validEntities.filterNot { it.locked }.sumOf { it.sizeBytes },
            lockedBytes = validEntities.filter { it.locked }.sumOf { it.sizeBytes },
            normalClipCount = validEntities.count { !it.locked },
            lockedClipCount = validEntities.count { it.locked },
        )
    }

    suspend fun upsertFile(file: File?, dashcamRoot: File, locked: Boolean? = null) {
        if (file == null || !file.exists() || !file.isFile) return
        val entity = file.toEntityOrNull(
            dashcamRoot = dashcamRoot,
            locked = locked ?: file.isLockedSegment(),
            unknownDateLabel = unknownDateLabel,
        ) ?: return
        dao.insert(entity)
    }

    suspend fun deleteByFile(file: File?, dashcamRoot: File) {
        if (file == null) return
        val path = file.toDashcamPathOrNull(dashcamRoot) ?: return
        dao.deleteByPath(path)
    }

    suspend fun deleteByDashcamPath(path: String?) {
        if (path.isNullOrBlank()) return
        dao.deleteByPath(path)
    }

    suspend fun deleteMissing(entity: RecordingSegmentIndexEntity) {
        dao.deleteByPath(entity.dashcamPath)
    }

    private suspend fun RecordingSegmentIndexEntity.toRecordingSegmentOrNull(
        dashcamRoot: File,
        removeStale: Boolean,
    ): RecordingSegment? {
        val file = File(dashcamRoot, dashcamPath)
        if (!file.exists() || !file.isFile) {
            if (removeStale) dao.deleteByPath(dashcamPath)
            return null
        }
        val fresh = withFreshFileStats(file)
        if (fresh != this) dao.insert(fresh)
        return fresh.toRecordingSegment(dashcamRoot)
    }

    private fun scanSegments(root: File, dashcamRoot: File, locked: Boolean): List<RecordingSegmentIndexEntity> {
        if (!root.exists()) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.extension.equals("mp4", ignoreCase = true) }
            .mapNotNull { file -> file.toEntityOrNull(dashcamRoot, locked, unknownDateLabel) }
            .toList()
    }

    private companion object {
        const val KEY_IMPORTED = "imported"
    }
}

data class RecordingSegmentStorageSnapshot(
    val normalBytes: Long,
    val lockedBytes: Long,
    val normalClipCount: Int,
    val lockedClipCount: Int,
)

@Entity(tableName = "recording_segments")
data class RecordingSegmentIndexEntity(
    @PrimaryKey val dashcamPath: String,
    val name: String,
    val groupKey: String,
    val day: String,
    val cameraDirection: String,
    val locked: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
) {
    fun toRecordingSegment(dashcamRoot: File): RecordingSegment {
        return RecordingSegment(
            name = name,
            relativePath = dashcamPath.removeManagedPrefix(locked),
            groupKey = groupKey,
            absolutePath = File(dashcamRoot, dashcamPath).absolutePath,
            day = day,
            cameraDirection = if (cameraDirection == CameraDirection.Front.name) {
                CameraDirection.Front
            } else {
                CameraDirection.Rear
            },
            locked = locked,
            sizeBytes = sizeBytes,
            lastModifiedMillis = lastModifiedMillis,
        )
    }

    fun withFreshFileStats(file: File): RecordingSegmentIndexEntity {
        val size = file.length()
        val modified = file.lastModified()
        return if (size == sizeBytes && modified == lastModifiedMillis) {
            this
        } else {
            copy(sizeBytes = size, lastModifiedMillis = modified)
        }
    }
}

@Dao
interface RecordingSegmentIndexDao {
    @Query("SELECT * FROM recording_segments ORDER BY lastModifiedMillis DESC, dashcamPath ASC LIMIT :limit")
    suspend fun listRecent(limit: Int): List<RecordingSegmentIndexEntity>

    @Query("SELECT * FROM recording_segments")
    suspend fun listAll(): List<RecordingSegmentIndexEntity>

    @Query("SELECT * FROM recording_segments WHERE locked = 0 ORDER BY lastModifiedMillis ASC, dashcamPath ASC")
    suspend fun listNormal(): List<RecordingSegmentIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(segment: RecordingSegmentIndexEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<RecordingSegmentIndexEntity>)

    @Query("DELETE FROM recording_segments")
    suspend fun deleteAll()

    @Query("DELETE FROM recording_segments WHERE dashcamPath = :dashcamPath")
    suspend fun deleteByPath(dashcamPath: String)

    suspend fun replaceAll(segments: List<RecordingSegmentIndexEntity>) {
        deleteAll()
        if (segments.isNotEmpty()) insertAll(segments)
    }
}

@Database(
    entities = [RecordingSegmentIndexEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RecordingSegmentIndexDatabase : RoomDatabase() {
    abstract fun recordingSegmentIndexDao(): RecordingSegmentIndexDao

    companion object {
        @Volatile
        private var instance: RecordingSegmentIndexDatabase? = null

        fun from(context: Context): RecordingSegmentIndexDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RecordingSegmentIndexDatabase::class.java,
                    "voyagecam_recording_segments.db",
                ).build().also { instance = it }
            }
        }
    }
}

private fun File.toEntityOrNull(
    dashcamRoot: File,
    locked: Boolean,
    unknownDateLabel: String,
): RecordingSegmentIndexEntity? {
    val dashcamPath = toDashcamPathOrNull(dashcamRoot) ?: return null
    val relativePath = dashcamPath.removeManagedPrefix(locked)
    val direction = when {
        name.contains("_front", ignoreCase = true) -> CameraDirection.Front
        else -> CameraDirection.Rear
    }
    return RecordingSegmentIndexEntity(
        dashcamPath = dashcamPath,
        name = name,
        groupKey = relativePath.toSegmentGroupKey(),
        day = relativePath.toSegmentDay(unknownDateLabel),
        cameraDirection = direction.name,
        locked = locked,
        sizeBytes = length(),
        lastModifiedMillis = lastModified(),
    )
}

private fun File.toDashcamPathOrNull(dashcamRoot: File): String? {
    val root = runCatching { dashcamRoot.canonicalFile.toPath() }.getOrNull() ?: return null
    val filePath = runCatching { canonicalFile.toPath() }.getOrNull() ?: return null
    if (!filePath.startsWith(root)) return null
    return root.relativize(filePath).toString()
}

private fun File.isLockedSegment(): Boolean {
    return path.contains("${File.separator}locked${File.separator}") || nameWithoutExtension.endsWith("_locked")
}

private fun String.removeManagedPrefix(locked: Boolean): String {
    val prefix = if (locked) "locked/" else "normal/"
    val normalized = replace(File.separatorChar, '/')
    return normalized.removePrefix(prefix)
}

private fun String.toSegmentDay(unknownDateLabel: String): String {
    return replace(File.separatorChar, '/')
        .substringBefore('/', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?: unknownDateLabel
}

private fun String.toSegmentGroupKey(): String {
    val normalized = replace(File.separatorChar, '/')
    return normalized.substringBeforeLast('/', missingDelimiterValue = normalized)
}
