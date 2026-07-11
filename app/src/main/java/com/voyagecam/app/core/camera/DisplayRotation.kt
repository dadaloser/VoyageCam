package com.voyagecam.app.core.camera

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.core.content.getSystemService

internal fun Context.displayAssociatedContext(): Context {
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

internal fun Context.safeDisplayRotation(): Int {
    val displayManager = applicationContext.getSystemService<DisplayManager>()
        ?: getSystemService<DisplayManager>()
        ?: return Surface.ROTATION_0
    @Suppress("DEPRECATION")
    val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        ?: displayManager.displays.firstOrNull()
    return display?.rotation ?: Surface.ROTATION_0
}
