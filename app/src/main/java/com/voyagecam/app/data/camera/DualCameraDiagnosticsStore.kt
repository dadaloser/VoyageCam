package com.voyagecam.app.data.camera

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
import com.voyagecam.app.core.model.DualCameraDiagnostic
import com.voyagecam.app.core.model.DualCameraDiagnosticStage
import com.voyagecam.app.core.model.PersistedDualCameraDiagnostic
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DualCameraDiagnosticsStore(context: Context) {
    private val appContext = context.applicationContext
    private val database = DualCameraPersistenceDatabase.from(appContext)
    private val dao = database.dualCameraDiagnosticsDao()
    private val legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    private val legacyImportMutex = Mutex()
    @Volatile
    private var legacyImportChecked = false

    suspend fun load(): PersistedDualCameraDiagnostic? {
        ensureLegacyImported()
        return dao.load()?.toPersistedModel()
    }

    suspend fun record(diagnostic: DualCameraDiagnostic) {
        ensureLegacyImported()
        dao.upsert(
            DualCameraDiagnosticEntity(
                id = SINGLETON_RECORD_ID,
                stage = diagnostic.stage.name,
                detail = diagnostic.detail,
                recordedAtMillis = System.currentTimeMillis(),
            ),
        )
        clearLegacyPrefs()
    }

    suspend fun clear() {
        ensureLegacyImported()
        dao.clear()
        clearLegacyPrefs()
    }

    private suspend fun ensureLegacyImported() {
        if (legacyImportChecked) return

        legacyImportMutex.withLock {
            if (legacyImportChecked) return

            if (dao.count() == 0) {
                legacyPrefs.toPersistedDualCameraDiagnosticOrNull()?.let { diagnostic ->
                    dao.upsert(
                        DualCameraDiagnosticEntity(
                            id = SINGLETON_RECORD_ID,
                            stage = diagnostic.stage.name,
                            detail = diagnostic.detail,
                            recordedAtMillis = diagnostic.recordedAtMillis,
                        ),
                    )
                }
            }
            clearLegacyPrefs()
            legacyImportChecked = true
        }
    }

    private fun clearLegacyPrefs() {
        legacyPrefs.edit().clear().apply()
    }

    private companion object {
        private const val LEGACY_PREFS_NAME = "voyage_cam_dual_camera_diagnostics"
        private const val LEGACY_KEY_STAGE = "stage"
        private const val LEGACY_KEY_DETAIL = "detail"
        private const val LEGACY_KEY_RECORDED_AT = "recorded_at"
        private const val SINGLETON_RECORD_ID = 1
    }

    private fun DualCameraDiagnosticEntity.toPersistedModel(): PersistedDualCameraDiagnostic? {
        val stageValue = stage.toDualCameraDiagnosticStage() ?: return null
        val nonBlankDetail = detail.takeIf { it.isNotBlank() } ?: return null
        val persistedAt = recordedAtMillis.takeIf { it > 0L } ?: return null
        return PersistedDualCameraDiagnostic(
            diagnostic = DualCameraDiagnostic(
                stage = stageValue,
                detail = nonBlankDetail,
            ),
            recordedAtMillis = persistedAt,
        )
    }

    private fun android.content.SharedPreferences.toPersistedDualCameraDiagnosticOrNull(): PersistedDualCameraDiagnostic? {
        val stageValue = getString(LEGACY_KEY_STAGE, null)?.toDualCameraDiagnosticStage() ?: return null
        val nonBlankDetail = getString(LEGACY_KEY_DETAIL, null)?.takeIf { it.isNotBlank() } ?: return null
        val persistedAt = getLong(LEGACY_KEY_RECORDED_AT, 0L).takeIf { it > 0L } ?: return null
        return PersistedDualCameraDiagnostic(
            diagnostic = DualCameraDiagnostic(
                stage = stageValue,
                detail = nonBlankDetail,
            ),
            recordedAtMillis = persistedAt,
        )
    }
}

@Entity(tableName = "dual_camera_diagnostics")
data class DualCameraDiagnosticEntity(
    @PrimaryKey val id: Int,
    val stage: String,
    val detail: String,
    val recordedAtMillis: Long,
)

@Entity(tableName = "dual_camera_session_telemetry")
data class DualCameraSessionTelemetryEntity(
    @PrimaryKey val id: Int,
    val summary: String,
    val detail: String,
    val diagnostic: String?,
    val recordedAtMillis: Long,
)

@Dao
interface DualCameraDiagnosticsDao {
    @Query("SELECT * FROM dual_camera_diagnostics WHERE id = :id LIMIT 1")
    suspend fun load(id: Int = 1): DualCameraDiagnosticEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DualCameraDiagnosticEntity)

    @Query("DELETE FROM dual_camera_diagnostics")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM dual_camera_diagnostics")
    suspend fun count(): Int
}

@Dao
interface DualCameraSessionTelemetryDao {
    @Query("SELECT * FROM dual_camera_session_telemetry WHERE id = :id LIMIT 1")
    suspend fun load(id: Int = 1): DualCameraSessionTelemetryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DualCameraSessionTelemetryEntity)

    @Query("DELETE FROM dual_camera_session_telemetry")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM dual_camera_session_telemetry")
    suspend fun count(): Int
}

@Database(
    entities = [
        DualCameraDiagnosticEntity::class,
        DualCameraSessionTelemetryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class DualCameraPersistenceDatabase : RoomDatabase() {
    abstract fun dualCameraDiagnosticsDao(): DualCameraDiagnosticsDao
    abstract fun dualCameraSessionTelemetryDao(): DualCameraSessionTelemetryDao

    companion object {
        @Volatile
        private var instance: DualCameraPersistenceDatabase? = null

        fun from(context: Context): DualCameraPersistenceDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DualCameraPersistenceDatabase::class.java,
                    "voyagecam_dual_camera_persistence.db",
                ).build().also { instance = it }
            }
        }
    }
}

internal fun String.toDualCameraDiagnosticStage(): DualCameraDiagnosticStage? {
    return runCatching { DualCameraDiagnosticStage.valueOf(this) }.getOrNull()
}
