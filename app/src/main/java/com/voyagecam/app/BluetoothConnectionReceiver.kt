package com.voyagecam.app

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class BluetoothConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BluetoothDevice.ACTION_ACL_CONNECTED) return

        val settings = VoyageCamSettingsStore(context).load()
        if (!settings.autoStartOnTrustedBluetooth) return
        if (settings.trustedBluetoothDevice.isBlank()) return
        if (!context.hasBluetoothConnectPermission()) return

        val device = intent.getParcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        if (!device.matchesTrustedDevice(settings.trustedBluetoothDevice)) return

        RecordingAutoStartPolicy(context).startIfAllowed(settings)
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

    @Suppress("DEPRECATION")
    private inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            getParcelableExtra(key) as? T
        }
    }
}
