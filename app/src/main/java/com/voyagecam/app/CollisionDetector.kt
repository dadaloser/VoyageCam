package com.voyagecam.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class CollisionDetector(
    context: Context,
    private val sensitivity: CollisionSensitivity,
    private val onCollision: (CollisionEvent) -> Unit,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val usesLinearAcceleration = sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION
    private var lastTriggerTimestampMillis = 0L

    fun start(): Boolean {
        val activeSensor = sensor ?: return false
        return sensorManager.registerListener(
            this,
            activeSensor,
            SensorManager.SENSOR_DELAY_GAME,
        )
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val accelerationG = event.values.toAccelerationG()
        if (accelerationG < sensitivity.thresholdG) return

        val now = System.currentTimeMillis()
        if (now - lastTriggerTimestampMillis < TRIGGER_COOLDOWN_MILLIS) return
        lastTriggerTimestampMillis = now

        onCollision(
            CollisionEvent(
                accelerationG = accelerationG,
                thresholdG = sensitivity.thresholdG,
                triggeredAtMillis = now,
            ),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun FloatArray.toAccelerationG(): Float {
        val x = getOrElse(0) { 0f }
        val y = getOrElse(1) { 0f }
        val z = getOrElse(2) { 0f }
        val metersPerSecondSquared = sqrt(x * x + y * y + z * z)
        val linearAcceleration = if (usesLinearAcceleration) {
            metersPerSecondSquared
        } else {
            (metersPerSecondSquared - SensorManager.GRAVITY_EARTH).coerceAtLeast(0f)
        }

        return linearAcceleration / SensorManager.GRAVITY_EARTH
    }

    data class CollisionEvent(
        val accelerationG: Float,
        val thresholdG: Float,
        val triggeredAtMillis: Long,
    )

    companion object {
        private const val TRIGGER_COOLDOWN_MILLIS = 10_000L
    }
}
