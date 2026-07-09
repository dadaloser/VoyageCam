package com.voyagecam.app.ui.settings

import androidx.annotation.StringRes
import com.voyagecam.app.core.model.DualCameraSwitchState
import com.voyagecam.app.ui.labelRes

@StringRes
fun DualCameraSwitchState.asLabelRes(): Int = labelRes()
