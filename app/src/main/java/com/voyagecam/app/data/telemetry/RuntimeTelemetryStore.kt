package com.voyagecam.app.data.telemetry

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
import com.voyagecam.app.core.model.DualCameraDiagnosticStage
import com.voyagecam.app.core.model.DualCameraFailureSource
import com.voyagecam.app.core.model.PersistedCrashReport
import com.voyagecam.app.core.model.PersistedDualCameraFailureArchive
import com.voyagecam.app.core.model.PersistedStructuredLogEntry
import com.voyagecam.app.core.model.StructuredLogLevel
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64

class RuntimeTelemetryStore(context: Context) {
    private val appContext = context.applicationContext
    private val database = RuntimeTelemetryDatabase.from(appContext)
    private val logDao = database.runtimeLogDao()
    private val crashDao = database.crashReportDao()
    private val failureDao = database.dualCameraFailureArchiveDao()

    suspend fun recentLogs(limit: Int = DEFAULT_LOG_LIMIT): List<PersistedStructuredLogEntry> {
        return logDao.recent(limit).map(RuntimeLogEntity::toModel)
    }

    suspend fun recordLog(
        level: StructuredLogLevel,
        category: String,
        event: String,
        message: String,
        attributes: Map<String, String> = emptyMap(),
        throwable: Throwable? = null,
    ) {
        logDao.insert(
            RuntimeLogEntity(
                level = level.name,
                category = category,
                event = event,
                message = message,
                attributes = flattenAttributes(attributes),
                throwable = throwable?.stackTraceToString(),
                recordedAtMillis = System.currentTimeMillis(),
            ),
        )
        logDao.trim(MAX_LOG_ROWS)
    }

    suspend fun clearLogs() {
        logDao.clear()
    }

    suspend fun latestCrashReport(): PersistedCrashReport? {
        return crashDao.latest()?.toModel()
    }

    suspend fun importPendingCrashReportIfPresent(): PersistedCrashReport? {
        val file = pendingCrashFile(appContext)
        if (!file.exists() || !file.isFile) return null
        val payload = runCatching {
            PendingCrashReportCodec.decode(file.readText(StandardCharsets.UTF_8))
        }.getOrNull() ?: return null.also { file.delete() }

        crashDao.insert(
            CrashReportEntity(
                threadName = payload.threadName,
                exceptionType = payload.exceptionType,
                message = payload.message,
                stacktrace = payload.stacktrace,
                appVersion = payload.appVersion,
                recordedAtMillis = payload.recordedAtMillis,
            ),
        )
        crashDao.trim(MAX_CRASH_ROWS)
        file.delete()
        return crashDao.latest()?.toModel()
    }

    suspend fun clearCrashReports() {
        crashDao.clear()
    }

    suspend fun archiveDualCameraFailure(
        source: DualCameraFailureSource,
        stage: DualCameraDiagnosticStage?,
        summary: String,
        detail: String,
        attributes: Map<String, String> = emptyMap(),
    ) {
        failureDao.insert(
            DualCameraFailureArchiveEntity(
                source = source.name,
                stage = stage?.name,
                summary = summary,
                detail = detail,
                attributes = flattenAttributes(attributes),
                recordedAtMillis = System.currentTimeMillis(),
            ),
        )
        failureDao.trim(MAX_FAILURE_ROWS)
    }

    suspend fun recentDualCameraFailures(limit: Int = DEFAULT_FAILURE_LIMIT): List<PersistedDualCameraFailureArchive> {
        return failureDao.recent(limit).mapNotNull(DualCameraFailureArchiveEntity::toModel)
    }

    suspend fun clearDualCameraFailures() {
        failureDao.clear()
    }

    companion object {
        fun writePendingCrashReport(
            context: Context,
            threadName: String,
            throwable: Throwable,
        ) {
            val file = pendingCrashFile(context.applicationContext)
            file.parentFile?.mkdirs()
            file.writeText(
                PendingCrashReportCodec.encode(
                    PendingCrashReportPayload(
                        threadName = threadName,
                        exceptionType = throwable::class.java.name,
                        message = throwable.message,
                        stacktrace = throwable.stackTraceToString(),
                        appVersion = context.appVersionLabel(),
                        recordedAtMillis = System.currentTimeMillis(),
                    ),
                ),
                StandardCharsets.UTF_8,
            )
        }

        private fun pendingCrashFile(context: Context): File {
            return File(File(context.filesDir, TELEMETRY_DIR_NAME), PENDING_CRASH_FILE_NAME)
        }

        private fun Context.appVersionLabel(): String {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName ?: "unknown"
            val versionCode = packageInfo.longVersionCode
            return "$versionName ($versionCode)"
        }

        private const val TELEMETRY_DIR_NAME = "runtime_telemetry"
        private const val PENDING_CRASH_FILE_NAME = "pending_crash.txt"
        private const val DEFAULT_LOG_LIMIT = 8
        private const val DEFAULT_FAILURE_LIMIT = 8
        private const val MAX_LOG_ROWS = 200
        private const val MAX_CRASH_ROWS = 10
        private const val MAX_FAILURE_ROWS = 50
    }
}

@Entity(tableName = "runtime_logs")
data class RuntimeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: String,
    val category: String,
    val event: String,
    val message: String,
    val attributes: String,
    val throwable: String?,
    val recordedAtMillis: Long,
)

@Entity(tableName = "crash_reports")
data class CrashReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadName: String,
    val exceptionType: String,
    val message: String?,
    val stacktrace: String,
    val appVersion: String,
    val recordedAtMillis: Long,
)

@Entity(tableName = "dual_camera_failure_archive")
data class DualCameraFailureArchiveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val stage: String?,
    val summary: String,
    val detail: String,
    val attributes: String,
    val recordedAtMillis: Long,
)

@Dao
interface RuntimeLogDao {
    @Query("SELECT * FROM runtime_logs ORDER BY recordedAtMillis DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<RuntimeLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RuntimeLogEntity)

    @Query("DELETE FROM runtime_logs")
    suspend fun clear()

    @Query(
        """
        DELETE FROM runtime_logs
        WHERE id NOT IN (
            SELECT id FROM runtime_logs
            ORDER BY recordedAtMillis DESC, id DESC
            LIMIT :keepRows
        )
        """,
    )
    suspend fun trim(keepRows: Int)
}

@Dao
interface CrashReportDao {
    @Query("SELECT * FROM crash_reports ORDER BY recordedAtMillis DESC, id DESC LIMIT 1")
    suspend fun latest(): CrashReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CrashReportEntity)

    @Query("DELETE FROM crash_reports")
    suspend fun clear()

    @Query(
        """
        DELETE FROM crash_reports
        WHERE id NOT IN (
            SELECT id FROM crash_reports
            ORDER BY recordedAtMillis DESC, id DESC
            LIMIT :keepRows
        )
        """,
    )
    suspend fun trim(keepRows: Int)
}

@Dao
interface DualCameraFailureArchiveDao {
    @Query("SELECT * FROM dual_camera_failure_archive ORDER BY recordedAtMillis DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<DualCameraFailureArchiveEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DualCameraFailureArchiveEntity)

    @Query("DELETE FROM dual_camera_failure_archive")
    suspend fun clear()

    @Query(
        """
        DELETE FROM dual_camera_failure_archive
        WHERE id NOT IN (
            SELECT id FROM dual_camera_failure_archive
            ORDER BY recordedAtMillis DESC, id DESC
            LIMIT :keepRows
        )
        """,
    )
    suspend fun trim(keepRows: Int)
}

@Database(
    entities = [
        RuntimeLogEntity::class,
        CrashReportEntity::class,
        DualCameraFailureArchiveEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class RuntimeTelemetryDatabase : RoomDatabase() {
    abstract fun runtimeLogDao(): RuntimeLogDao
    abstract fun crashReportDao(): CrashReportDao
    abstract fun dualCameraFailureArchiveDao(): DualCameraFailureArchiveDao

    companion object {
        @Volatile
        private var instance: RuntimeTelemetryDatabase? = null

        fun from(context: Context): RuntimeTelemetryDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RuntimeTelemetryDatabase::class.java,
                    "voyagecam_runtime_telemetry.db",
                ).build().also { instance = it }
            }
        }
    }
}

internal data class PendingCrashReportPayload(
    val threadName: String,
    val exceptionType: String,
    val message: String?,
    val stacktrace: String,
    val appVersion: String,
    val recordedAtMillis: Long,
)

internal object PendingCrashReportCodec {
    fun encode(payload: PendingCrashReportPayload): String {
        return listOf(
            payload.recordedAtMillis.toString(),
            payload.threadName.encodeField(),
            payload.exceptionType.encodeField(),
            (payload.message ?: "").encodeField(),
            payload.stacktrace.encodeField(),
            payload.appVersion.encodeField(),
        ).joinToString(separator = "\n")
    }

    fun decode(raw: String): PendingCrashReportPayload? {
        val fields = raw.split('\n')
        if (fields.size < 6) return null
        val recordedAtMillis = fields[0].toLongOrNull() ?: return null
        return PendingCrashReportPayload(
            threadName = fields[1].decodeField() ?: return null,
            exceptionType = fields[2].decodeField() ?: return null,
            message = fields[3].decodeField(),
            stacktrace = fields[4].decodeField() ?: return null,
            appVersion = fields[5].decodeField() ?: return null,
            recordedAtMillis = recordedAtMillis,
        )
    }

    private fun String.encodeField(): String {
        return Base64.getEncoder().encodeToString(toByteArray(StandardCharsets.UTF_8))
    }

    private fun String.decodeField(): String? {
        return runCatching {
            String(Base64.getDecoder().decode(this), StandardCharsets.UTF_8)
        }.getOrNull()
    }
}

internal fun flattenAttributes(attributes: Map<String, String>): String {
    if (attributes.isEmpty()) return ""
    return attributes
        .toSortedMap()
        .entries
        .joinToString(separator = " | ") { (key, value) -> "$key=$value" }
}

private fun RuntimeLogEntity.toModel(): PersistedStructuredLogEntry {
    return PersistedStructuredLogEntry(
        id = id,
        level = runCatching { StructuredLogLevel.valueOf(level) }.getOrDefault(StructuredLogLevel.Info),
        category = category,
        event = event,
        message = message,
        attributes = attributes,
        throwable = throwable,
        recordedAtMillis = recordedAtMillis,
    )
}

private fun CrashReportEntity.toModel(): PersistedCrashReport {
    return PersistedCrashReport(
        id = id,
        threadName = threadName,
        exceptionType = exceptionType,
        message = message,
        stacktrace = stacktrace,
        appVersion = appVersion,
        recordedAtMillis = recordedAtMillis,
    )
}

private fun DualCameraFailureArchiveEntity.toModel(): PersistedDualCameraFailureArchive? {
    return PersistedDualCameraFailureArchive(
        id = id,
        source = runCatching { DualCameraFailureSource.valueOf(source) }.getOrNull() ?: return null,
        stage = stage?.let { runCatching { DualCameraDiagnosticStage.valueOf(it) }.getOrNull() },
        summary = summary,
        detail = detail,
        attributes = attributes,
        recordedAtMillis = recordedAtMillis,
    )
}
