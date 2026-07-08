package com.voyagecam.app.feature.autostart

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
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
                reason = "可信蓝牙自动启动开关未开启",
            )
            return
        }
        if (settings.trustedBluetoothDevice.isBlank()) {
            diagnostics.record(
                source = AutoStartSource.Bluetooth,
                result = AutoStartResult.Ignored,
                reason = "未填写可信蓝牙设备",
            )
            return
        }
        if (!context.hasBluetoothConnectPermission()) {
            diagnostics.record(
                source = AutoStartSource.Bluetooth,
                result = AutoStartResult.Ignored,
                reason = "蓝牙权限未授权",
                detail = settings.trustedBluetoothDevice,
            )
            return
        }

        val device = intent.getParcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        if (device == null) {
            diagnostics.record(
                source = AutoStartSource.Bluetooth,
                result = AutoStartResult.Ignored,
                reason = "连接广播未包含蓝牙设备信息",
                detail = settings.trustedBluetoothDevice,
            )
            return
        }

        val deviceSummary = device.safeSummary()
        if (!device.matchesTrustedDevice(settings.trustedBluetoothDevice)) {
            diagnostics.record(
                source = AutoStartSource.Bluetooth,
                result = AutoStartResult.Ignored,
                reason = "连接设备与可信设备不匹配",
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
            reason = blockedReason ?: "可信蓝牙已连接并启动录制",
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
