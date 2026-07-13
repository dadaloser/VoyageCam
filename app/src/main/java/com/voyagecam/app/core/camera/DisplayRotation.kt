package com.voyagecam.app.core.camera

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.core.content.getSystemService

internal fun Context.displayAssociatedContext(): Context {
    findActivity()?.let { return it }
    val baseContext = applicationContext ?: this
    val displayManager = baseContext.getSystemService<DisplayManager>()
        ?: getSystemService<DisplayManager>()
        ?: return baseContext
    @Suppress("DEPRECATION")
    val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        ?: displayManager.displays.firstOrNull()
        ?: return baseContext
    return runCatching { baseContext.createDisplayContext(display) }
        .getOrDefault(baseContext)
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext?.findActivity()
        else -> null
    }
}

internal fun Context.safeDisplayRotation(): Int {
    return runCatching {
        val displayManager = applicationContext.getSystemService<DisplayManager>()
            ?: getSystemService<DisplayManager>()
            ?: return Surface.ROTATION_0
        @Suppress("DEPRECATION")
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            ?: displayManager.displays.firstOrNull()
        display?.rotation ?: Surface.ROTATION_0
    }.getOrDefault(Surface.ROTATION_0)
}
