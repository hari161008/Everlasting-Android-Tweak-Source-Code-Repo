package com.coolappstore.everlastingandroidtweak.features.securitymotion

import android.content.Context
import android.hardware.*
import android.os.Handler
import android.os.Looper
import kotlin.math.sqrt

class SecurityMotionManager(private val context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    var sensitivity = 0.5f
    var onMotionDetected: (() -> Unit)? = null

    // Calibration
    private val gravityEma = FloatArray(3)
    private var calibrationCount = 0
    private val CALIBRATION_SAMPLES = 40   // ~4 s at SENSOR_DELAY_NORMAL
    private var calibrated = false

    // Cooldown
    private var lastAlarm = 0L
    private val COOLDOWN_MS = 6_000L

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val v = event.values

        // Build exponential moving average of gravity baseline
        val alpha = 0.9f
        for (i in 0..2) gravityEma[i] = alpha * gravityEma[i] + (1f - alpha) * v[i]
        calibrationCount++
        if (calibrationCount < CALIBRATION_SAMPLES) return
        calibrated = true

        // Dynamic acceleration (subtract gravity baseline)
        val dx = v[0] - gravityEma[0]
        val dy = v[1] - gravityEma[1]
        val dz = v[2] - gravityEma[2]
        val mag = sqrt(dx * dx + dy * dy + dz * dz)

        // threshold: 1.5 m/s² (high sensitivity) .. 4.5 m/s² (low)
        val threshold = 1.5f + (1f - sensitivity) * 3.0f
        val now = System.currentTimeMillis()
        if (mag > threshold && now - lastAlarm > COOLDOWN_MS) {
            lastAlarm = now
            handler.post { onMotionDetected?.invoke() }
        }
    }

    fun start() {
        if (running) return
        calibrationCount = 0; calibrated = false; lastAlarm = 0L
        gravityEma.fill(0f)
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        running = true
    }

    fun stop() {
        if (!running) return
        sm.unregisterListener(this)
        handler.removeCallbacksAndMessages(null)
        running = false
    }

    override fun onAccuracyChanged(s: Sensor, a: Int) {}
}
