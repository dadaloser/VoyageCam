package com.voyagecam.app.data.settings

import android.content.Context
import android.os.StatFs
import com.voyagecam.app.R
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.core.model.CollisionSensitivity
import com.voyagecam.app.core.model.DeviceCapabilityGrade
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.DualCameraFailureReason
import com.voyagecam.app.core.model.DualCameraProbeResult
import com.voyagecam.app.core.model.DualCameraProbeStatus
import com.voyagecam.app.core.model.DualCameraSwitchState

enum class RecordingMode {
    RearOnly,
    FrontOnly,
    Auto,
}

enum class RecordingOrientationStrategy {
    FollowSystem,
    FixedLandscapeDriving,
}

enum class RecordingResolutionPreset(val label: String) {
    HD_720P("720p"),
    FHD_1080P("1080p"),
    UHD_2160P("4K"),
}

enum class RecordingFrameRatePreset(val label: String, val fps: Int) {
    FPS_24("24fps", 24),
    FPS_30("30fps", 30),
    FPS_60("60fps", 60),
}

enum class RecordingBitratePreset(val label: String, val megabitsPerSecond: Int) {
    MBPS_8("8Mbps", 8),
    MBPS_12("12Mbps", 12),
    MBPS_16("16Mbps", 16),
    MBPS_24("24Mbps", 24);

    val bitsPerSecond: Int
        get() = megabitsPerSecond * 1_000_000
}

data class RecordingVideoProfile(
    val resolution: RecordingResolutionPreset,
    val frameRate: RecordingFrameRatePreset,
    val bitrate: RecordingBitratePreset,
)

enum class RecordingConfigDowngradeReason {
    FrontCameraUnavailable,
    DualCameraUnavailable,
    DualCameraProfileUnsupported,
}

data class ResolvedRecordingConfig(
    val requestedMode: RecordingMode,
    val primaryCameraDirection: CameraDirection,
    val dualCameraActive: Boolean,
    val frontCameraActive: Boolean,
    val ambientAudioActive: Boolean,
    val frontCameraMirrorActive: Boolean,
    val orientationStrategy: RecordingOrientationStrategy,
    val downgradeReason: RecordingConfigDowngradeReason? = null,
)

data class VoyageCamSettings(
    val recordingMode: RecordingMode = RecordingMode.RearOnly,
    val storageCapacityGb: Int = 10,
    val segmentDurationMinutes: Int = 3,
    val recordingResolution: RecordingResolutionPreset = RecordingResolutionPreset.FHD_1080P,
    val recordingFrameRate: RecordingFrameRatePreset = RecordingFrameRatePreset.FPS_30,
    val recordingBitrate: RecordingBitratePreset = RecordingBitratePreset.MBPS_12,
    val collisionSensitivity: CollisionSensitivity = CollisionSensitivity.Medium,
    val frontCameraMirrorEnabled: Boolean = false,
    val recordingOrientationStrategy: RecordingOrientationStrategy = RecordingOrientationStrategy.FollowSystem,
    val ambientAudioEnabled: Boolean = false,
    val gpsMetadataEnabled: Boolean = false,
    val exportWatermarkSubtitlesEnabled: Boolean = false,
    val exportBurnedWatermarkVideoEnabled: Boolean = false,
    val thermalGuardEnabled: Boolean = true,
    val lowBatteryGuardEnabled: Boolean = true,
    val slowSegmentGuardEnabled: Boolean = true,
    val autoStartOnPowerConnected: Boolean = false,
    val autoStartOnTrustedBluetooth: Boolean = false,
    val trustedBluetoothDevice: String = "",
) {
    val dualCameraEnabled: Boolean
        get() = recordingMode == RecordingMode.Auto
}

class VoyageCamSettingsStore(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences("voyage_cam_settings", Context.MODE_PRIVATE)

    fun load(): VoyageCamSettings {
        return VoyageCamSettings(
            recordingMode = prefs.getString(
                KEY_RECORDING_MODE,
                null,
            ).toRecordingMode(
                legacyDualCameraEnabled = prefs.getBoolean(KEY_DUAL_CAMERA_ENABLED, false),
            ),
            storageCapacityGb = prefs.getInt(KEY_STORAGE_CAPACITY_GB, DEFAULT_STORAGE_GB),
            segmentDurationMinutes = prefs.getInt(KEY_SEGMENT_DURATION_MINUTES, DEFAULT_SEGMENT_DURATION_MINUTES)
                .coerceToAllowedSegmentDuration(),
            recordingResolution = prefs.getString(
                KEY_RECORDING_RESOLUTION,
                RecordingResolutionPreset.FHD_1080P.name,
            ).toRecordingResolutionPreset(),
            recordingFrameRate = prefs.getString(
                KEY_RECORDING_FRAME_RATE,
                RecordingFrameRatePreset.FPS_30.name,
            ).toRecordingFrameRatePreset(),
            recordingBitrate = prefs.getString(
                KEY_RECORDING_BITRATE,
                RecordingBitratePreset.MBPS_12.name,
            ).toRecordingBitratePreset(),
            collisionSensitivity = prefs.getString(
                KEY_COLLISION_SENSITIVITY,
                CollisionSensitivity.Medium.name,
            ).toCollisionSensitivity(),
            frontCameraMirrorEnabled = prefs.getBoolean(KEY_FRONT_CAMERA_MIRROR_ENABLED, false),
            recordingOrientationStrategy = prefs.getString(
                KEY_RECORDING_ORIENTATION_STRATEGY,
                RecordingOrientationStrategy.FollowSystem.name,
            ).toRecordingOrientationStrategy(),
            ambientAudioEnabled = prefs.getBoolean(KEY_AMBIENT_AUDIO_ENABLED, false),
            gpsMetadataEnabled = prefs.getBoolean(KEY_GPS_METADATA_ENABLED, false),
            exportWatermarkSubtitlesEnabled = prefs.getBoolean(KEY_EXPORT_WATERMARK_SUBTITLES_ENABLED, false),
            exportBurnedWatermarkVideoEnabled = prefs.getBoolean(KEY_EXPORT_BURNED_WATERMARK_VIDEO_ENABLED, false),
            thermalGuardEnabled = prefs.getBoolean(KEY_THERMAL_GUARD_ENABLED, true),
            lowBatteryGuardEnabled = prefs.getBoolean(KEY_LOW_BATTERY_GUARD_ENABLED, true),
            slowSegmentGuardEnabled = prefs.getBoolean(KEY_SLOW_SEGMENT_GUARD_ENABLED, true),
            autoStartOnPowerConnected = prefs.getBoolean(KEY_AUTO_START_ON_POWER_CONNECTED, false),
            autoStartOnTrustedBluetooth = prefs.getBoolean(KEY_AUTO_START_ON_TRUSTED_BLUETOOTH, false),
            trustedBluetoothDevice = prefs.getString(KEY_TRUSTED_BLUETOOTH_DEVICE, null).orEmpty(),
        )
    }

    fun save(settings: VoyageCamSettings) {
        prefs.edit()
            .putBoolean(KEY_DUAL_CAMERA_ENABLED, settings.dualCameraEnabled)
            .putString(KEY_RECORDING_MODE, settings.recordingMode.name)
            .putInt(KEY_STORAGE_CAPACITY_GB, settings.storageCapacityGb)
            .putInt(KEY_SEGMENT_DURATION_MINUTES, settings.segmentDurationMinutes.coerceToAllowedSegmentDuration())
            .putString(KEY_RECORDING_RESOLUTION, settings.recordingResolution.name)
            .putString(KEY_RECORDING_FRAME_RATE, settings.recordingFrameRate.name)
            .putString(KEY_RECORDING_BITRATE, settings.recordingBitrate.name)
            .putString(KEY_COLLISION_SENSITIVITY, settings.collisionSensitivity.name)
            .putBoolean(KEY_FRONT_CAMERA_MIRROR_ENABLED, settings.frontCameraMirrorEnabled)
            .putString(KEY_RECORDING_ORIENTATION_STRATEGY, settings.recordingOrientationStrategy.name)
            .putBoolean(KEY_AMBIENT_AUDIO_ENABLED, settings.ambientAudioEnabled)
            .putBoolean(KEY_GPS_METADATA_ENABLED, settings.gpsMetadataEnabled)
            .putBoolean(KEY_EXPORT_WATERMARK_SUBTITLES_ENABLED, settings.exportWatermarkSubtitlesEnabled)
            .putBoolean(KEY_EXPORT_BURNED_WATERMARK_VIDEO_ENABLED, settings.exportBurnedWatermarkVideoEnabled)
            .putBoolean(KEY_THERMAL_GUARD_ENABLED, settings.thermalGuardEnabled)
            .putBoolean(KEY_LOW_BATTERY_GUARD_ENABLED, settings.lowBatteryGuardEnabled)
            .putBoolean(KEY_SLOW_SEGMENT_GUARD_ENABLED, settings.slowSegmentGuardEnabled)
            .putBoolean(KEY_AUTO_START_ON_POWER_CONNECTED, settings.autoStartOnPowerConnected)
            .putBoolean(KEY_AUTO_START_ON_TRUSTED_BLUETOOTH, settings.autoStartOnTrustedBluetooth)
            .putString(KEY_TRUSTED_BLUETOOTH_DEVICE, settings.trustedBluetoothDevice.trim())
            .apply()
    }

    fun loadCapability(): DualCameraCapability? {
        val stateName = prefs.getString(KEY_CAPABILITY_STATE, null) ?: return null
        val state = runCatching { DualCameraSwitchState.valueOf(stateName) }.getOrNull() ?: return null
        val grade = runCatching {
            DeviceCapabilityGrade.valueOf(prefs.getString(KEY_CAPABILITY_GRADE, DeviceCapabilityGrade.D.name).orEmpty())
        }.getOrDefault(DeviceCapabilityGrade.D)

        return DualCameraCapability(
            state = state,
            grade = grade,
            rearCameraId = prefs.getString(KEY_CAPABILITY_REAR_ID, null),
            frontCameraId = prefs.getString(KEY_CAPABILITY_FRONT_ID, null),
            reason = prefs.getString(KEY_CAPABILITY_REASON, null)
                ?: context.getString(R.string.settings_capability_default_reason),
            rearSummary = prefs.getString(KEY_CAPABILITY_REAR_SUMMARY, null)
                ?: context.getString(R.string.settings_capability_not_checked),
            frontSummary = prefs.getString(KEY_CAPABILITY_FRONT_SUMMARY, null)
                ?: context.getString(R.string.settings_capability_not_checked),
            systemSummary = prefs.getString(KEY_CAPABILITY_SYSTEM_SUMMARY, null)
                ?: context.getString(R.string.settings_capability_unknown_system),
            checkedAtMillis = prefs.getLong(KEY_CAPABILITY_CHECKED_AT, System.currentTimeMillis()),
            failureReason = prefs.getString(KEY_CAPABILITY_FAILURE_REASON, null)
                ?.let { stored -> runCatching { DualCameraFailureReason.valueOf(stored) }.getOrNull() },
            previewProbe = DualCameraProbeResult(
                status = prefs.getString(KEY_CAPABILITY_PREVIEW_STATUS, null)
                    ?.let { stored -> runCatching { DualCameraProbeStatus.valueOf(stored) }.getOrNull() }
                    ?: DualCameraProbeStatus.NotChecked,
                detail = prefs.getString(KEY_CAPABILITY_PREVIEW_DETAIL, null)
                    ?: context.getString(R.string.settings_capability_not_checked),
            ),
            recordingProbe = DualCameraProbeResult(
                status = prefs.getString(KEY_CAPABILITY_RECORDING_STATUS, null)
                    ?.let { stored -> runCatching { DualCameraProbeStatus.valueOf(stored) }.getOrNull() }
                    ?: DualCameraProbeStatus.NotChecked,
                detail = prefs.getString(KEY_CAPABILITY_RECORDING_DETAIL, null)
                    ?: context.getString(R.string.settings_capability_not_checked),
            ),
            encodingProbe = DualCameraProbeResult(
                status = prefs.getString(KEY_CAPABILITY_ENCODING_STATUS, null)
                    ?.let { stored -> runCatching { DualCameraProbeStatus.valueOf(stored) }.getOrNull() }
                    ?: DualCameraProbeStatus.NotChecked,
                detail = prefs.getString(KEY_CAPABILITY_ENCODING_DETAIL, null)
                    ?: context.getString(R.string.settings_capability_not_checked),
            ),
        )
    }

    fun saveCapability(capability: DualCameraCapability) {
        prefs.edit()
            .putString(KEY_CAPABILITY_STATE, capability.state.name)
            .putString(KEY_CAPABILITY_GRADE, capability.grade.name)
            .putString(KEY_CAPABILITY_REAR_ID, capability.rearCameraId)
            .putString(KEY_CAPABILITY_FRONT_ID, capability.frontCameraId)
            .putString(KEY_CAPABILITY_REASON, capability.reason)
            .putString(KEY_CAPABILITY_REAR_SUMMARY, capability.rearSummary)
            .putString(KEY_CAPABILITY_FRONT_SUMMARY, capability.frontSummary)
            .putString(KEY_CAPABILITY_SYSTEM_SUMMARY, capability.systemSummary)
            .putLong(KEY_CAPABILITY_CHECKED_AT, capability.checkedAtMillis)
            .putString(KEY_CAPABILITY_FAILURE_REASON, capability.failureReason?.name)
            .putString(KEY_CAPABILITY_PREVIEW_STATUS, capability.previewProbe.status.name)
            .putString(KEY_CAPABILITY_PREVIEW_DETAIL, capability.previewProbe.detail)
            .putString(KEY_CAPABILITY_RECORDING_STATUS, capability.recordingProbe.status.name)
            .putString(KEY_CAPABILITY_RECORDING_DETAIL, capability.recordingProbe.detail)
            .putString(KEY_CAPABILITY_ENCODING_STATUS, capability.encodingProbe.status.name)
            .putString(KEY_CAPABILITY_ENCODING_DETAIL, capability.encodingProbe.detail)
            .apply()
    }

    companion object {
        const val MIN_STORAGE_GB = 2
        const val MAX_STORAGE_GB = 512
        val ALLOWED_SEGMENT_DURATIONS_MINUTES = listOf(1, 3, 5)

        private const val DEFAULT_STORAGE_GB = 10
        private const val DEFAULT_SEGMENT_DURATION_MINUTES = 3
        private const val KEY_DUAL_CAMERA_ENABLED = "dual_camera_enabled"
        private const val KEY_RECORDING_MODE = "recording_mode"
        private const val KEY_STORAGE_CAPACITY_GB = "storage_capacity_gb"
        private const val KEY_SEGMENT_DURATION_MINUTES = "segment_duration_minutes"
        private const val KEY_RECORDING_RESOLUTION = "recording_resolution"
        private const val KEY_RECORDING_FRAME_RATE = "recording_frame_rate"
        private const val KEY_RECORDING_BITRATE = "recording_bitrate"
        private const val KEY_COLLISION_SENSITIVITY = "collision_sensitivity"
        private const val KEY_FRONT_CAMERA_MIRROR_ENABLED = "front_camera_mirror_enabled"
        private const val KEY_RECORDING_ORIENTATION_STRATEGY = "recording_orientation_strategy"
        private const val KEY_AMBIENT_AUDIO_ENABLED = "ambient_audio_enabled"
        private const val KEY_GPS_METADATA_ENABLED = "gps_metadata_enabled"
        private const val KEY_EXPORT_WATERMARK_SUBTITLES_ENABLED = "export_watermark_subtitles_enabled"
        private const val KEY_EXPORT_BURNED_WATERMARK_VIDEO_ENABLED = "export_burned_watermark_video_enabled"
        private const val KEY_THERMAL_GUARD_ENABLED = "thermal_guard_enabled"
        private const val KEY_LOW_BATTERY_GUARD_ENABLED = "low_battery_guard_enabled"
        private const val KEY_SLOW_SEGMENT_GUARD_ENABLED = "slow_segment_guard_enabled"
        private const val KEY_AUTO_START_ON_POWER_CONNECTED = "auto_start_on_power_connected"
        private const val KEY_AUTO_START_ON_TRUSTED_BLUETOOTH = "auto_start_on_trusted_bluetooth"
        private const val KEY_TRUSTED_BLUETOOTH_DEVICE = "trusted_bluetooth_device"
        private const val KEY_CAPABILITY_STATE = "capability_state"
        private const val KEY_CAPABILITY_GRADE = "capability_grade"
        private const val KEY_CAPABILITY_REAR_ID = "capability_rear_id"
        private const val KEY_CAPABILITY_FRONT_ID = "capability_front_id"
        private const val KEY_CAPABILITY_REASON = "capability_reason"
        private const val KEY_CAPABILITY_REAR_SUMMARY = "capability_rear_summary"
        private const val KEY_CAPABILITY_FRONT_SUMMARY = "capability_front_summary"
        private const val KEY_CAPABILITY_SYSTEM_SUMMARY = "capability_system_summary"
        private const val KEY_CAPABILITY_CHECKED_AT = "capability_checked_at"
        private const val KEY_CAPABILITY_FAILURE_REASON = "capability_failure_reason"
        private const val KEY_CAPABILITY_PREVIEW_STATUS = "capability_preview_status"
        private const val KEY_CAPABILITY_PREVIEW_DETAIL = "capability_preview_detail"
        private const val KEY_CAPABILITY_RECORDING_STATUS = "capability_recording_status"
        private const val KEY_CAPABILITY_RECORDING_DETAIL = "capability_recording_detail"
        private const val KEY_CAPABILITY_ENCODING_STATUS = "capability_encoding_status"
        private const val KEY_CAPABILITY_ENCODING_DETAIL = "capability_encoding_detail"

        fun Int.coerceToAllowedSegmentDuration(): Int {
            return if (this in ALLOWED_SEGMENT_DURATIONS_MINUTES) {
                this
            } else {
                DEFAULT_SEGMENT_DURATION_MINUTES
            }
        }

        private fun String?.toCollisionSensitivity(): CollisionSensitivity {
            return runCatching {
                CollisionSensitivity.valueOf(this ?: CollisionSensitivity.Medium.name)
            }.getOrDefault(CollisionSensitivity.Medium)
        }

        private fun String?.toRecordingMode(legacyDualCameraEnabled: Boolean): RecordingMode {
            return runCatching {
                RecordingMode.valueOf(this ?: if (legacyDualCameraEnabled) RecordingMode.Auto.name else RecordingMode.RearOnly.name)
            }.getOrDefault(if (legacyDualCameraEnabled) RecordingMode.Auto else RecordingMode.RearOnly)
        }

        private fun String?.toRecordingResolutionPreset(): RecordingResolutionPreset {
            return runCatching {
                RecordingResolutionPreset.valueOf(this ?: RecordingResolutionPreset.FHD_1080P.name)
            }.getOrDefault(RecordingResolutionPreset.FHD_1080P)
        }

        private fun String?.toRecordingFrameRatePreset(): RecordingFrameRatePreset {
            return runCatching {
                RecordingFrameRatePreset.valueOf(this ?: RecordingFrameRatePreset.FPS_30.name)
            }.getOrDefault(RecordingFrameRatePreset.FPS_30)
        }

        private fun String?.toRecordingBitratePreset(): RecordingBitratePreset {
            return runCatching {
                RecordingBitratePreset.valueOf(this ?: RecordingBitratePreset.MBPS_12.name)
            }.getOrDefault(RecordingBitratePreset.MBPS_12)
        }

        private fun String?.toRecordingOrientationStrategy(): RecordingOrientationStrategy {
            return runCatching {
                RecordingOrientationStrategy.valueOf(this ?: RecordingOrientationStrategy.FollowSystem.name)
            }.getOrDefault(RecordingOrientationStrategy.FollowSystem)
        }
    }
}

data class StorageCapacityLimit(val maxGb: Int) {
    companion object {
        private const val BYTES_PER_GB = 1024L * 1024L * 1024L

        fun from(context: Context): StorageCapacityLimit {
            val statFs = StatFs(context.filesDir.absolutePath)
            val eightyPercentAvailableGb = (statFs.availableBytes * 0.8 / BYTES_PER_GB).toInt()
            val maxGb = maxOf(
                VoyageCamSettingsStore.MIN_STORAGE_GB,
                minOf(VoyageCamSettingsStore.MAX_STORAGE_GB, eightyPercentAvailableGb),
            )

            return StorageCapacityLimit(maxGb = maxGb)
        }
    }
}

fun VoyageCamSettings.coerceTo(limit: StorageCapacityLimit): VoyageCamSettings {
    return copy(
        storageCapacityGb = storageCapacityGb.coerceIn(
            VoyageCamSettingsStore.MIN_STORAGE_GB,
            limit.maxGb,
        ),
        segmentDurationMinutes = with(VoyageCamSettingsStore) {
            segmentDurationMinutes.coerceToAllowedSegmentDuration()
        },
    )
}

fun VoyageCamSettings.recordingVideoProfile(): RecordingVideoProfile {
    return RecordingVideoProfile(
        resolution = recordingResolution,
        frameRate = recordingFrameRate,
        bitrate = recordingBitrate,
    )
}

fun VoyageCamSettings.resolveRecordingConfig(capability: DualCameraCapability?): ResolvedRecordingConfig {
    val hasFrontCamera = capability?.frontCameraId != null
    val dualCameraAvailable = capability?.isAvailable == true
    val dualCameraProfileSupported = recordingVideoProfile().supportsDualCamera()

    return when (recordingMode) {
        RecordingMode.RearOnly -> {
            ResolvedRecordingConfig(
                requestedMode = recordingMode,
                primaryCameraDirection = CameraDirection.Rear,
                dualCameraActive = false,
                frontCameraActive = false,
                ambientAudioActive = ambientAudioEnabled,
                frontCameraMirrorActive = false,
                orientationStrategy = recordingOrientationStrategy,
            )
        }

        RecordingMode.FrontOnly -> {
            if (hasFrontCamera) {
                ResolvedRecordingConfig(
                    requestedMode = recordingMode,
                    primaryCameraDirection = CameraDirection.Front,
                    dualCameraActive = false,
                    frontCameraActive = true,
                    ambientAudioActive = false,
                    frontCameraMirrorActive = frontCameraMirrorEnabled,
                    orientationStrategy = recordingOrientationStrategy,
                )
            } else {
                ResolvedRecordingConfig(
                    requestedMode = recordingMode,
                    primaryCameraDirection = CameraDirection.Rear,
                    dualCameraActive = false,
                    frontCameraActive = false,
                    ambientAudioActive = ambientAudioEnabled,
                    frontCameraMirrorActive = false,
                    orientationStrategy = recordingOrientationStrategy,
                    downgradeReason = RecordingConfigDowngradeReason.FrontCameraUnavailable,
                )
            }
        }

        RecordingMode.Auto -> {
            when {
                dualCameraAvailable && dualCameraProfileSupported -> {
                    ResolvedRecordingConfig(
                        requestedMode = recordingMode,
                        primaryCameraDirection = CameraDirection.Rear,
                        dualCameraActive = true,
                        frontCameraActive = true,
                        ambientAudioActive = ambientAudioEnabled,
                        frontCameraMirrorActive = frontCameraMirrorEnabled,
                        orientationStrategy = recordingOrientationStrategy,
                    )
                }

                dualCameraAvailable -> {
                    ResolvedRecordingConfig(
                        requestedMode = recordingMode,
                        primaryCameraDirection = CameraDirection.Rear,
                        dualCameraActive = false,
                        frontCameraActive = false,
                        ambientAudioActive = ambientAudioEnabled,
                        frontCameraMirrorActive = false,
                        orientationStrategy = recordingOrientationStrategy,
                        downgradeReason = RecordingConfigDowngradeReason.DualCameraProfileUnsupported,
                    )
                }

                else -> {
                    ResolvedRecordingConfig(
                        requestedMode = recordingMode,
                        primaryCameraDirection = CameraDirection.Rear,
                        dualCameraActive = false,
                        frontCameraActive = false,
                        ambientAudioActive = ambientAudioEnabled,
                        frontCameraMirrorActive = false,
                        orientationStrategy = recordingOrientationStrategy,
                        downgradeReason = RecordingConfigDowngradeReason.DualCameraUnavailable,
                    )
                }
            }
        }
    }
}

fun VoyageCamSettings.estimatedManagedBytesPerMinute(capability: DualCameraCapability?): Long {
    val resolved = resolveRecordingConfig(capability)
    return estimatedManagedBytesPerMinute(resolved)
}

fun VoyageCamSettings.estimatedManagedBytesPerMinute(resolvedConfig: ResolvedRecordingConfig): Long {
    val videoStreamBytesPerMinute = recordingBitrate.bitsPerSecond.toLong() * 60L / 8L
    val streamCount = if (resolvedConfig.dualCameraActive) 2L else 1L
    return videoStreamBytesPerMinute * streamCount +
        if (resolvedConfig.ambientAudioActive) AUDIO_BYTES_PER_MINUTE else 0L
}

fun VoyageCamSettings.estimatedManagedBytesPerMinute(dualCameraActive: Boolean): Long {
    val videoStreamBytesPerMinute = recordingBitrate.bitsPerSecond.toLong() * 60L / 8L
    val streamCount = if (dualCameraActive) 2L else 1L
    return videoStreamBytesPerMinute * streamCount +
        if (ambientAudioEnabled) AUDIO_BYTES_PER_MINUTE else 0L
}

fun RecordingVideoProfile.supportsDualCamera(): Boolean {
    return resolution != RecordingResolutionPreset.UHD_2160P &&
        frameRate != RecordingFrameRatePreset.FPS_60 &&
        bitrate.megabitsPerSecond <= DUAL_CAMERA_MAX_BITRATE_MBPS
}

private const val AUDIO_BYTES_PER_MINUTE = 1L * 1024L * 1024L
private const val DUAL_CAMERA_MAX_BITRATE_MBPS = 12
