package com.voyagecam.app

import android.app.Application
import com.tencent.bugly.crashreport.CrashReport
import com.voyagecam.app.data.recording.RecordingStartupRecovery
import com.voyagecam.app.data.telemetry.VoyageCamRuntimeTelemetry

class VoyageCamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeBugly()
        VoyageCamRuntimeTelemetry.initialize(this)
        VoyageCamRuntimeTelemetry.installCrashMonitor(this)
        RecordingStartupRecovery.warmUp(this)
    }

    private fun initializeBugly() {
        val strategy = CrashReport.UserStrategy(this).apply {
            appVersion = BuildConfig.VERSION_NAME
        }
        CrashReport.initCrashReport(
            applicationContext,
            BuildConfig.BUGLY_APP_ID,
            BuildConfig.BUGLY_DEBUG,
            strategy,
        )
    }
}
