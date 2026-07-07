package com.voyagecam.app.ui.events

import java.io.File

sealed class EvidenceExportState {
    abstract val eventId: String

    data class Running(
        override val eventId: String,
        val title: String,
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
}
