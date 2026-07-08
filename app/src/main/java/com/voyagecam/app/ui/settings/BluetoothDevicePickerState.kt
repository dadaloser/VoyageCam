package com.voyagecam.app.ui.settings

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.voyagecam.app.core.model.TrustedBluetoothDevice
import java.util.Locale

private const val MAX_TRUSTED_BLUETOOTH_LENGTH = 64

@Stable
class BluetoothDevicePickerState(
    private val loadPairedDevices: () -> List<TrustedBluetoothDevice>,
    trustedDeviceInput: String,
) {
    var pairedDevices by mutableStateOf(loadPairedDevices())
        private set

    var trustedDeviceInput by mutableStateOf(trustedDeviceInput.take(MAX_TRUSTED_BLUETOOTH_LENGTH))
        private set

    fun refreshPairedDevices() {
        pairedDevices = loadPairedDevices()
    }

    fun updateTrustedDeviceInput(value: String): String {
        val sanitized = value.take(MAX_TRUSTED_BLUETOOTH_LENGTH)
        trustedDeviceInput = sanitized
        return sanitized
    }

    fun selectDevice(device: TrustedBluetoothDevice): String {
        return updateTrustedDeviceInput(device.preferredMatchValue())
    }

    fun syncTrustedDeviceInput(value: String) {
        val sanitized = value.take(MAX_TRUSTED_BLUETOOTH_LENGTH)
        if (sanitized != trustedDeviceInput) {
            trustedDeviceInput = sanitized
        }
    }
}

@Composable
fun rememberBluetoothDevicePickerState(
    context: Context,
    trustedDeviceInput: String,
): BluetoothDevicePickerState {
    val pickerState = remember(context) {
        BluetoothDevicePickerState(
            loadPairedDevices = { context.pairedBluetoothDevices() },
            trustedDeviceInput = trustedDeviceInput,
        )
    }
    LaunchedEffect(trustedDeviceInput) {
        pickerState.syncTrustedDeviceInput(trustedDeviceInput)
    }
    return pickerState
}

private fun Context.pairedBluetoothDevices(): List<TrustedBluetoothDevice> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }

    return runCatching {
        bluetoothAdapter()
            ?.bondedDevices
            .orEmpty()
            .map { device ->
                TrustedBluetoothDevice(
                    name = runCatching { device.name }.getOrNull().orEmpty(),
                    address = runCatching { device.address }.getOrNull().orEmpty(),
                )
            }
            .filter { it.name.isNotBlank() || it.address.isNotBlank() }
            .sortedWith(compareBy<TrustedBluetoothDevice> { it.name.ifBlank { it.address }.lowercase(Locale.getDefault()) })
    }.getOrDefault(emptyList())
}

private fun Context.bluetoothAdapter(): BluetoothAdapter? {
    return getSystemService(BluetoothManager::class.java)?.adapter
}
