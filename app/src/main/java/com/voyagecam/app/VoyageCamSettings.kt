package com.voyagecam.app

import android.content.Context

data class VoyageCamSettings(
    val dualCameraEnabled: Boolean = false,
    val storageCapacityGb: Int = 10,
    val segmentDurationMinutes: Int = 3,
    val collisionSensitivity: CollisionSensitivity = CollisionSensitivity.Medium,
    val ambientAudioEnabled: Boolean = false,
    val autoStartOnPowerConnected: Boolean = false,
    val autoStartOnTrustedBluetooth: Boolean = false,
    val trustedBluetoothDevice: String = "",
)

class VoyageCamSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("voyage_cam_settings", Context.MODE_PRIVATE)

    fun load(): VoyageCamSettings {
        return VoyageCamSettings(
            dualCameraEnabled = prefs.getBoolean(KEY_DUAL_CAMERA_ENABLED, false),
            storageCapacityGb = prefs.getInt(KEY_STORAGE_CAPACITY_GB, DEFAULT_STORAGE_GB),
            segmentDurationMinutes = prefs.getInt(KEY_SEGMENT_DURATION_MINUTES, DEFAULT_SEGMENT_DURATION_MINUTES)
                .coerceToAllowedSegmentDuration(),
            collisionSensitivity = prefs.getString(
                KEY_COLLISION_SENSITIVITY,
                CollisionSensitivity.Medium.name,
            ).toCollisionSensitivity(),
            ambientAudioEnabled = prefs.getBoolean(KEY_AMBIENT_AUDIO_ENABLED, false),
            autoStartOnPowerConnected = prefs.getBoolean(KEY_AUTO_START_ON_POWER_CONNECTED, false),
            autoStartOnTrustedBluetooth = prefs.getBoolean(KEY_AUTO_START_ON_TRUSTED_BLUETOOTH, false),
            trustedBluetoothDevice = prefs.getString(KEY_TRUSTED_BLUETOOTH_DEVICE, null).orEmpty(),
        )
    }

    fun save(settings: VoyageCamSettings) {
        prefs.edit()
            .putBoolean(KEY_DUAL_CAMERA_ENABLED, settings.dualCameraEnabled)
            .putInt(KEY_STORAGE_CAPACITY_GB, settings.storageCapacityGb)
            .putInt(KEY_SEGMENT_DURATION_MINUTES, settings.segmentDurationMinutes.coerceToAllowedSegmentDuration())
            .putString(KEY_COLLISION_SENSITIVITY, settings.collisionSensitivity.name)
            .putBoolean(KEY_AMBIENT_AUDIO_ENABLED, settings.ambientAudioEnabled)
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
            reason = prefs.getString(KEY_CAPABILITY_REASON, null) ?: "未保存检测原因",
            rearSummary = prefs.getString(KEY_CAPABILITY_REAR_SUMMARY, null) ?: "未检测",
            frontSummary = prefs.getString(KEY_CAPABILITY_FRONT_SUMMARY, null) ?: "未检测",
            systemSummary = prefs.getString(KEY_CAPABILITY_SYSTEM_SUMMARY, null) ?: "未知系统",
            checkedAtMillis = prefs.getLong(KEY_CAPABILITY_CHECKED_AT, System.currentTimeMillis()),
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
            .apply()
    }

    companion object {
        const val MIN_STORAGE_GB = 2
        const val MAX_STORAGE_GB = 512
        val ALLOWED_SEGMENT_DURATIONS_MINUTES = listOf(1, 3, 5)

        private const val DEFAULT_STORAGE_GB = 10
        private const val DEFAULT_SEGMENT_DURATION_MINUTES = 3
        private const val KEY_DUAL_CAMERA_ENABLED = "dual_camera_enabled"
        private const val KEY_STORAGE_CAPACITY_GB = "storage_capacity_gb"
        private const val KEY_SEGMENT_DURATION_MINUTES = "segment_duration_minutes"
        private const val KEY_COLLISION_SENSITIVITY = "collision_sensitivity"
        private const val KEY_AMBIENT_AUDIO_ENABLED = "ambient_audio_enabled"
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
    }
}
