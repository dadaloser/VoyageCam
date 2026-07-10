package com.voyagecam.app.ui.events
import java.io.File

sealed class EvidenceExportState {
    abstract val exportId: String

    data class Running(
        override val exportId: String,
        val title: String,
        val progressPercent: Int = 0,
        val currentItem: String = "",
    ) : EvidenceExportState()

    data class Ready(
        override val exportId: String,
        val title: String,
        val file: File,
        val itemCount: Int,
    ) : EvidenceExportState()

    data class Failed(
        override val exportId: String,
        val title: String,
        val message: String,
    ) : EvidenceExportState()

    data class Cancelled(
        override val exportId: String,
        val title: String,
        val message: String = "",
    ) : EvidenceExportState()
}
