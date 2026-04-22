package com.coolappstore.everlastingandroidtweak.features.camera

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.VibratorManager
import kotlin.math.abs

/**
 * TwistCameraManager — opens camera when wrist is twisted twice quickly.
 *
 * ROOT CAUSE FIXES:
 *
 * (1) VIBRATION FIRED REGARDLESS OF POCKET STATE:
 *     Old: vibrated on every detected twist event, even when in pocket.
 *     Fix: vibration only occurs in triggerCamera() after proximity check. ✓
 *
 * (2) PROXIMITY SENSOR LIFECYCLE:
 *     Old: registered for 200ms snapshot (async, unreliable, battery drain pattern).
 *     Fix: register permanently alongside gyroscope in start(), read cached value. ✓
 *
 * (3) PROXIMITY LOGIC CLARIFIED:
 *     value >= maximumRange = NOT covered = free in hand/open air → ALLOW camera
 *     value < maximumRange  = covered = in pocket → BLOCK camera
 *     Current TwistCamera logic was already correct for allow-when-open;
 *     just synced to permanent registration model to match ShakeTorch behavior.
 */
class TwistCameraManager(private val context: Context) : SensorEventListener {

    private val sensorManager   = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope       = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val vibrator        = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    private var lastTwistTime = 0L
    private var twistCount    = 0
    private var isRunning     = false
    private var _proximityEnabled  = false
    private var proximityRegistered = false
    private var sensitivity   = 3.5f

    private val TWIST_WINDOW_MS = 700L
    private val REQUIRED_TWISTS = 2

    // true = covered/in pocket
    @Volatile private var proximityCovered = false

    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_PROXIMITY)
                proximityCovered = event.values[0] < event.sensor.maximumRange
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    fun setSensitivity(value: Float) { sensitivity = value }
    fun setProximityEnabled(enabled: Boolean) {
        _proximityEnabled = enabled
        if (isRunning) {
            if (enabled && proximitySensor != null && !proximityRegistered) {
                sensorManager.registerListener(proximityListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
                proximityRegistered = true
            } else if (!enabled && proximityRegistered) {
                sensorManager.unregisterListener(proximityListener)
                proximityRegistered = false
                proximityCovered = false
            }
        }
    }

    fun start() {
        if (isRunning) return
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            // Register proximity permanently
            if (_proximityEnabled && proximitySensor != null) {
                sensorManager.registerListener(proximityListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
                proximityRegistered = true
            }
            isRunning = true
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        if (proximityRegistered) {
            sensorManager.unregisterListener(proximityListener)
            proximityRegistered = false
        }
        proximityCovered = false
        isRunning = false
        twistCount = 0
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        val rotZ = event.values[2]
        val now  = System.currentTimeMillis()
        if (abs(rotZ) > sensitivity) {
            if (now - lastTwistTime > TWIST_WINDOW_MS) twistCount = 1 else twistCount++
            lastTwistTime = now
            if (twistCount >= REQUIRED_TWISTS) {
                twistCount = 0
                // FIX: check proximity before acting — no vibration/camera if in pocket
                if (_proximityEnabled && proximityCovered) return
                triggerCamera()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun triggerCamera() {
        // Vibration only here — after all proximity checks pass
        runCatching {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 120), -1))
        }
        try {
            context.startActivity(
                Intent("android.media.action.STILL_IMAGE_CAMERA")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) { e.printStackTrace() }
    }
}
