package com.voyagecam.app.core.model

data class TrustedBluetoothDevice(
    val name: String,
    val address: String,
) {
    fun preferredMatchValue(): String = name.ifBlank { address }

    fun displayLabel(): String {
        return when {
            name.isNotBlank() && address.isNotBlank() -> "$name · $address"
            name.isNotBlank() -> name
            else -> address
        }
    }
}
