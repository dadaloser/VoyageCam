package com.voyagecam.app.ui.events

import java.io.File

sealed class EvidenceExportState {
    abstract val eventId: String

    data class Running(
        override val eventId: String,
        val title: String,
        val progressPercent: Int = 0,
        val currentItem: String = "",
    ) : EvidenceExportState()

    data class Ready(
        override val eventId: String,
        val file: File,
        val clipCount: Int,
    ) : EvidenceExportState()

    data class Failed(
        override val eventId: String,
        val message: String,
    ) : EvidenceExportState()

    data class Cancelled(
        override val eventId: String,
        val message: String = "证据包导出已取消",
    ) : EvidenceExportState()
}
