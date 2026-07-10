package com.voyagecam.app.data.telemetry

import android.content.Context
import android.os.Process
import android.util.Log
import com.voyagecam.app.core.model.DualCameraDiagnostic
import com.voyagecam.app.core.model.DualCameraDiagnosticStage
import com.voyagecam.app.core.model.DualCameraFailureSource
import com.voyagecam.app.core.model.StructuredLogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object VoyageCamRuntimeTelemetry {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var store: RuntimeTelemetryStore? = null

    fun initialize(context: Context) {
        if (store != null) return
        val telemetryStore = RuntimeTelemetryStore(context.applicationContext)
        store = telemetryStore
        scope.launch {
            telemetryStore.importPendingCrashReportIfPresent()
            telemetryStore.recordLog(
                level = StructuredLogLevel.Info,
                category = CATEGORY_APP,
                event = "app_started",
                message = "VoyageCam application started",
            )
        }
    }

    fun installCrashMonitor(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                RuntimeTelemetryStore.writePendingCrashReport(
                    context = context.applicationContext,
                    threadName = thread.name,
                    throwable = throwable,
                )
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    fun log(
        level: StructuredLogLevel,
        category: String,
        event: String,
        message: String,
        attributes: Map<String, String> = emptyMap(),
        throwable: Throwable? = null,
    ) {
        writeToLogcat(level, category, event, message, attributes, throwable)
        store?.let { telemetryStore ->
            scope.launch {
                telemetryStore.recordLog(level, category, event, message, attributes, throwable)
            }
        }
    }

    fun archiveDualCameraFailure(
        source: DualCameraFailureSource,
        stage: DualCameraDiagnosticStage?,
        summary: String,
        detail: String,
        attributes: Map<String, String> = emptyMap(),
    ) {
        log(
            level = StructuredLogLevel.Warn,
            category = CATEGORY_DUAL_CAMERA,
            event = "dual_camera_failure",
            message = summary,
            attributes = buildMap {
                put("source", source.name)
                stage?.let { put("stage", it.name) }
                putAll(attributes)
            },
        )
        store?.let { telemetryStore ->
            scope.launch {
                telemetryStore.archiveDualCameraFailure(
                    source = source,
                    stage = stage,
                    summary = summary,
                    detail = detail,
                    attributes = attributes,
                )
            }
        }
    }

    fun archiveDualCameraFailure(
        source: DualCameraFailureSource,
        diagnostic: DualCameraDiagnostic,
        summary: String,
        attributes: Map<String, String> = emptyMap(),
    ) {
        archiveDualCameraFailure(
            source = source,
            stage = diagnostic.stage,
            summary = summary,
            detail = diagnostic.detail,
            attributes = attributes,
        )
    }

    private fun writeToLogcat(
        level: StructuredLogLevel,
        category: String,
        event: String,
        message: String,
        attributes: Map<String, String>,
        throwable: Throwable?,
    ) {
        val payload = buildString {
            append("event=")
            append(event)
            append(" message=")
            append(message)
            if (attributes.isNotEmpty()) {
                append(" fields=")
                append(flattenAttributes(attributes))
            }
        }
        when (level) {
            StructuredLogLevel.Debug -> Log.d(category, payload, throwable)
            StructuredLogLevel.Info -> Log.i(category, payload, throwable)
            StructuredLogLevel.Warn -> Log.w(category, payload, throwable)
            StructuredLogLevel.Error -> Log.e(category, payload, throwable)
            StructuredLogLevel.Fatal -> Log.wtf(category, payload, throwable)
        }
    }

    const val CATEGORY_APP = "VoyageCamApp"
    const val CATEGORY_RECORDING = "VoyageCamRecording"
    const val CATEGORY_DUAL_CAMERA = "VoyageCamDualCamera"
}
