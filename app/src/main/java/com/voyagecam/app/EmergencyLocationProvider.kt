package com.voyagecam.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

class EmergencyLocationProvider(private val context: Context) {
    private val locationManager: LocationManager? =
        context.getSystemService(LocationManager::class.java)

    fun currentSnapshot(): EmergencyLocationSnapshot? {
        if (!context.hasAnyLocationPermission()) return null
        val manager = locationManager ?: return null

        return runCatching {
            manager.allProviders
                .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.toEmergencyLocationSnapshot()
        }.getOrNull()
    }

    private fun Location.toEmergencyLocationSnapshot(): EmergencyLocationSnapshot {
        return EmergencyLocationSnapshot(
            latitude = latitude,
            longitude = longitude,
            speedMetersPerSecond = if (hasSpeed()) speed else null,
            capturedAtMillis = time.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
    }
}

data class EmergencyLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val speedMetersPerSecond: Float?,
    val capturedAtMillis: Long,
)

fun Context.hasAnyLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
}
