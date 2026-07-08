package com.voyagecam.app.ui.settings

import com.voyagecam.app.core.model.TrustedBluetoothDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothDevicePickerStateTest {
    @Test
    fun updateTrustedDeviceInput_truncatesLongValues() {
        val state = BluetoothDevicePickerState(
            loadPairedDevices = { emptyList() },
            trustedDeviceInput = "",
        )

        val value = state.updateTrustedDeviceInput("x".repeat(100))

        assertEquals(64, value.length)
        assertEquals(value, state.trustedDeviceInput)
    }

    @Test
    fun selectDevice_prefersDeviceName() {
        val state = BluetoothDevicePickerState(
            loadPairedDevices = { emptyList() },
            trustedDeviceInput = "",
        )

        val value = state.selectDevice(
            TrustedBluetoothDevice(
                name = "VoyageCam Car Kit",
                address = "AA:BB:CC:DD:EE:FF",
            ),
        )

        assertEquals("VoyageCam Car Kit", value)
        assertEquals(value, state.trustedDeviceInput)
    }

    @Test
    fun refreshPairedDevices_reloadsCurrentSource() {
        var source = listOf(TrustedBluetoothDevice(name = "A", address = "00"))
        val state = BluetoothDevicePickerState(
            loadPairedDevices = { source },
            trustedDeviceInput = "",
        )

        source = listOf(TrustedBluetoothDevice(name = "B", address = "11"))
        state.refreshPairedDevices()

        assertEquals(listOf(TrustedBluetoothDevice(name = "B", address = "11")), state.pairedDevices)
    }
}
