package com.voyagecam.app

import android.app.Application
import com.voyagecam.app.data.telemetry.VoyageCamRuntimeTelemetry

class VoyageCamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        VoyageCamRuntimeTelemetry.initialize(this)
        VoyageCamRuntimeTelemetry.installCrashMonitor(this)
    }
}
