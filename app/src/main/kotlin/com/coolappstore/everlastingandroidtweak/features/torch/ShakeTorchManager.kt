package com.coolappstore.everlastingandroidtweak.features.torch

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.VibrationEffect
import android.os.VibratorManager
import kotlin.math.sqrt

/**
 * ShakeTorchManager — detects phone shakes via accelerometer and toggles flashlight.
 *
 * ROOT CAUSE FIXES:
 *
 * (1) PROXIMITY LOGIC WAS INVERTED:
 *     Old: proximityBlocked = (value >= maximumRange)  → true when NOT covered (open air)
 *     Then: if (!proximityBlocked) → only triggered when IN POCKET. Completely backwards.
 *     Fix: proximityBlocked = (value < maximumRange)  → true when covered/in pocket
 *     Result: torch only triggers when phone is NOT in pocket (free in hand/open air). ✓
 *
 * (2) VIBRATION FIRED BEFORE PROXIMITY CHECK:
 *     Old code vibrated immediately on shake detection, before checking proximity.
 *     User felt vibration even when phone was in pocket, which is wrong.
 *     Fix: vibration now only occurs inside toggleTorch() after proximity check passes. ✓
 *
 * (3) PROXIMITY SENSOR LIFECYCLE:
 *     Old: registered for 200ms snapshot on each shake event (race condition, unreliable).
 *     Fix: register proximity alongside accelerometer in start(), keep it live while running.
 *     Value is read directly from the cached field on each shake event — no async delay. ✓
 *
 * (4) SENSITIVITY RANGE EXTENDED:
 *     Old range: 1–25 m/s². Extended to 1–50 m/s² for "very hard shake" use cases.
 */
class ShakeTorchManager(private val context: Context) : SensorEventListener {

    private val sensorManager   = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val cameraManager   = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val vibrator        = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    private val accelerometer   = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var lastX = 0f; private var lastY = 0f; private var lastZ = 0f
    private var lastShakeTime = 0L
    private var torchOn = false
    private var sensitivity = 12f
    private var _proximityEnabled = false
    private var proximityRegistered = false

    // true = covered/in pocket  (value < maximumRange = object is near sensor)
    @Volatile private var proximityCovered = false

    var isRunning = false; private set

    // Permanent proximity listener — stays registered while feature is running
    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
                // FIX: value < maximumRange means covered/near (in pocket)
                //      value >= maximumRange means open/free (not in pocket)
                proximityCovered = event.values[0] < event.sensor.maximumRange
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    fun setSensitivity(value: Float) { sensitivity = value }
    fun setProximityEnabled(enabled: Boolean) {
        _proximityEnabled = enabled
        if (isRunning) {
            // Re-register or unregister proximity based on new setting
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
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        // Register proximity permanently alongside accelerometer
        if (_proximityEnabled && proximitySensor != null) {
            sensorManager.registerListener(proximityListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            proximityRegistered = true
        }
        isRunning = true
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        if (proximityRegistered) {
            sensorManager.unregisterListener(proximityListener)
            proximityRegistered = false
        }
        proximityCovered = false
        isRunning = false
        if (torchOn) toggleTorch()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        val delta = sqrt((x - lastX) * (x - lastX) + (y - lastY) * (y - lastY) + (z - lastZ) * (z - lastZ))
        lastX = x; lastY = y; lastZ = z
        val now = System.currentTimeMillis()
        if (delta > sensitivity && now - lastShakeTime > 800L) {
            lastShakeTime = now
            // FIX: check proximity BEFORE doing anything (no vibration if in pocket)
            if (_proximityEnabled && proximityCovered) return  // in pocket → ignore
            toggleTorch()  // vibration only happens inside toggleTorch()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun toggleTorch() {
        try {
            val id = cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            torchOn = !torchOn
            cameraManager.setTorchMode(id, torchOn)
            // Vibration only here — after all checks pass, never in pocket
            runCatching {
                val pattern = if (torchOn) longArrayOf(0, 40, 60, 40) else longArrayOf(0, 80)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun isTorchOn() = torchOn
}
