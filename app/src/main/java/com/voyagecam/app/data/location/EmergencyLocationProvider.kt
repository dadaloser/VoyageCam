package com.voyagecam.app.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.voyagecam.app.core.model.EmergencyLocationSnapshot

class EmergencyLocationProvider(private val context: Context) {
    private val locationManager: LocationManager? =
        context.getSystemService(LocationManager::class.java)
    private val recentSnapshots = ArrayDeque<EmergencyLocationSnapshot>()
    private var updatesStarted = false
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            addSnapshot(location.toEmergencyLocationSnapshot())
        }

        @Deprecated("Deprecated by Android platform")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }

    fun startUpdates() {
        if (updatesStarted || !context.hasAnyLocationPermission()) return
        val manager = locationManager ?: return
        val providers = manager.allProviders.filter { provider ->
            provider == LocationManager.GPS_PROVIDER || provider == LocationManager.NETWORK_PROVIDER
        }.ifEmpty { manager.allProviders }

        providers.forEach { provider ->
            runCatching {
                manager.requestLocationUpdates(
                    provider,
                    LOCATION_UPDATE_INTERVAL_MS,
                    LOCATION_UPDATE_MIN_DISTANCE_METERS,
                    listener,
                    Looper.getMainLooper(),
                )
            }
        }
        updatesStarted = true
        currentSnapshot()?.let(::addSnapshot)
    }

    fun stopUpdates() {
        if (!updatesStarted) return
        runCatching { locationManager?.removeUpdates(listener) }
        updatesStarted = false
    }

    fun currentSnapshot(): EmergencyLocationSnapshot? {
        if (!context.hasAnyLocationPermission()) return null
        val manager = locationManager ?: return null

        val snapshot = runCatching {
            manager.allProviders
                .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.toEmergencyLocationSnapshot()
        }.getOrNull()
        snapshot?.let(::addSnapshot)
        return snapshot ?: recentSnapshots.lastOrNull()
    }

    fun recentSnapshots(triggeredAtMillis: Long, retentionMillis: Long, limit: Int): List<EmergencyLocationSnapshot> {
        prune(triggeredAtMillis)
        val startMillis = triggeredAtMillis - retentionMillis
        val endMillis = triggeredAtMillis + LOCATION_UPDATE_INTERVAL_MS
        return recentSnapshots
            .filter { it.capturedAtMillis in startMillis..endMillis }
            .takeLast(limit)
    }

    private fun Location.toEmergencyLocationSnapshot(): EmergencyLocationSnapshot {
        return EmergencyLocationSnapshot(
            latitude = latitude,
            longitude = longitude,
            speedMetersPerSecond = if (hasSpeed()) speed else null,
            bearingDegrees = if (hasBearing()) bearing else null,
            capturedAtMillis = time.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
    }

    private fun addSnapshot(snapshot: EmergencyLocationSnapshot) {
        if (recentSnapshots.lastOrNull()?.capturedAtMillis == snapshot.capturedAtMillis) return
        recentSnapshots.addLast(snapshot)
        prune(System.currentTimeMillis())
    }

    private fun prune(nowMillis: Long) {
        val cutoff = nowMillis - LOCATION_RETENTION_MS
        while (recentSnapshots.firstOrNull()?.capturedAtMillis?.let { it < cutoff } == true) {
            recentSnapshots.removeFirst()
        }
        while (recentSnapshots.size > MAX_RECENT_LOCATIONS) {
            recentSnapshots.removeFirst()
        }
    }

    companion object {
        private const val LOCATION_UPDATE_INTERVAL_MS = 2_000L
        private const val LOCATION_UPDATE_MIN_DISTANCE_METERS = 1f
        private const val LOCATION_RETENTION_MS = 5 * 60 * 1000L
        private const val MAX_RECENT_LOCATIONS = 180
    }
}

fun Context.hasAnyLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
}
