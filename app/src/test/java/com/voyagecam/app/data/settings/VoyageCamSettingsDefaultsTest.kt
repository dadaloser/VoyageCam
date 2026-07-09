package com.voyagecam.app.data.settings

import org.junit.Assert.assertFalse
import org.junit.Test

class VoyageCamSettingsDefaultsTest {
    @Test
    fun defaults_keep_sensitive_metadata_disabled() {
        val settings = VoyageCamSettings()

        assertFalse(settings.gpsMetadataEnabled)
        assertFalse(settings.exportWatermarkSubtitlesEnabled)
        assertFalse(settings.exportBurnedWatermarkVideoEnabled)
    }
}
