package com.voyagecam.app.feature.autostart

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.voyagecam.app.R
import com.voyagecam.app.core.model.AutoStartResult
import com.voyagecam.app.core.model.AutoStartSource
import com.voyagecam.app.data.autostart.AutoStartDiagnosticsStore
import com.voyagecam.app.data.settings.VoyageCamSettingsStore

class BluetoothConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BluetoothDevice.ACTION_ACL_CONNECTED) return

        val diagnostics = AutoStartDiagnosticsStore(context)
        val settings = VoyageCamSettingsStore(context).load()
        if (!settings.autoStartOnTrustedBluetooth) {
            diagnostics.record(
                source = AutoStartSource.Bluetooth,
                result = AutoStartResult.Ignored,
                reason = context.getString(R.string.autostart_bt_toggle_off),
            )
            return
        }
        if (settings.trustedBluetoothDevice.isBlank()) {
            diagnostics.record(
                source = AutoStartSource.Bluetooth,
                result = AutoStartResult.Ignored,
                reason = context.getString(R.string.autostart_bt_device_missing),
            )
            return
        }
        if (!context.hasBluetoothConnectPermission()) {
            diagnostics.record(
                source = AutoStartSource.Bluetooth,
                result = AutoStartResult.Ignored,
                reason = context.getString(R.string.autostart_bt_permission_missing),
                detail = settings.trustedBluetoothDevice,
            )
            return
        }

        val device = intent.getParcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        if (device == null) {
            diagnostics.record(
                source = AutoStartSource.Bluetooth,
                result = AutoStartResult.Ignored,
                reason = context.getString(R.string.autostart_bt_device_info_missing),
                detail = settings.trustedBluetoothDevice,
            )
            return
        }

        val deviceSummary = device.safeSummary()
        if (!device.matchesTrustedDevice(settings.trustedBluetoothDevice)) {
            diagnostics.record(
                source = AutoStartSource.Bluetooth,
                result = AutoStartResult.Ignored,
                reason = context.getString(R.string.autostart_bt_mismatch),
                detail = deviceSummary,
            )
            return
        }

        val blockedReason = RecordingAutoStartPolicy(context).startIfAllowed(
            source = AutoStartSource.Bluetooth,
            settings = settings,
            detail = deviceSummary,
        )
        diagnostics.record(
            source = AutoStartSource.Bluetooth,
            result = if (blockedReason == null) AutoStartResult.Started else AutoStartResult.Ignored,
            reason = blockedReason ?: context.getString(R.string.autostart_bt_started),
            detail = deviceSummary,
        )
    }

    private fun Context.hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun BluetoothDevice.matchesTrustedDevice(value: String): Boolean {
        val trusted = value.trim()
        if (trusted.isBlank()) return false
        val addressMatches = runCatching {
            address.equals(trusted, ignoreCase = true)
        }.getOrDefault(false)
        val nameMatches = runCatching {
            name?.equals(trusted, ignoreCase = true) == true
        }.getOrDefault(false)

        return addressMatches || nameMatches
    }

    private fun BluetoothDevice.safeSummary(): String {
        val safeName = runCatching { name }.getOrNull().orEmpty()
        val safeAddress = runCatching { address }.getOrNull().orEmpty()
        return listOf(safeName, safeAddress)
            .filter { it.isNotBlank() }
            .joinToString(separator = " / ")
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            getParcelableExtra(key) as? T
        }
    }
}
