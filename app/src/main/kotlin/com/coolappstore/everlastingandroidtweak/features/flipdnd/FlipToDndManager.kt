package com.coolappstore.everlastingandroidtweak.features.flipdnd

import android.app.NotificationManager
import android.content.Context
import android.hardware.*

/**
 * FlipToDndManager — activates DND when the phone is flipped face-down AND the
 * proximity sensor is covered (confirming the screen is pressed against a surface).
 *
 * ROOT CAUSE FIX — previous implementation used accelerometer only:
 *   The accelerometer detects Z < -7 when face-down, but this fires even when
 *   the phone is tilted at any steep angle (e.g., propped up). It also never
 *   stops the proximity listener, draining battery constantly.
 *
 * NEW LOGIC (two-sensor confirmation):
 *   1. Accelerometer (TYPE_ACCELEROMETER) detects face-down orientation (Z < -6.5).
 *   2. Proximity sensor (TYPE_PROXIMITY) is registered ONLY after face-down is confirmed.
 *      If proximity reads covered (value < maximumRange) → phone is on a surface → DND ON.
 *   3. When phone is flipped back up (Z > -5), proximity is unregistered immediately to
 *      save battery, and DND is turned OFF.
 *
 * This prevents false triggers (phone tilted, but not flat), saves battery (proximity
 * only active when face-down), and gives double confirmation before toggling DND.
 */
class FlipToDndManager(private val context: Context) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val accelerometer   = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var running          = false
    private var isFaceDown       = false
    private var proximityRegistered = false
    @Volatile private var proximityCovered = false

    // Separate listener for proximity — registered/unregistered based on orientation
    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_PROXIMITY) return
            val nowCovered = event.values[0] < event.sensor.maximumRange
            if (proximityCovered == nowCovered) return  // no change
            proximityCovered = nowCovered
            updateDnd()
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    // Accelerometer listener — always active while running
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        // Z axis < -6.5 = phone face-down on a surface
        // Z axis > -5.0 = phone being picked up / face-up
        val z = event.values[2]
        val nowFaceDown = z < -6.5f

        if (nowFaceDown == isFaceDown) return  // orientation unchanged

        isFaceDown = nowFaceDown

        if (isFaceDown) {
            // Phone just went face-down — start monitoring proximity
            if (proximitySensor != null && !proximityRegistered) {
                sm.registerListener(proximityListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
                proximityRegistered = true
            } else if (proximitySensor == null) {
                // No proximity sensor on this device — fall back to accelerometer-only
                setDnd(true)
            }
        } else {
            // Phone lifted / face-up — stop proximity monitoring, turn DND off
            if (proximityRegistered) {
                sm.unregisterListener(proximityListener)
                proximityRegistered = false
            }
            proximityCovered = false
            setDnd(false)
        }
    }

    private fun updateDnd() {
        // DND ON only when face-down AND proximity is covered
        if (isFaceDown && proximityCovered) setDnd(true)
        else setDnd(false)
    }

    private fun setDnd(enable: Boolean) {
        if (!nm.isNotificationPolicyAccessGranted) return
        val target = if (enable)
            NotificationManager.INTERRUPTION_FILTER_NONE
        else
            NotificationManager.INTERRUPTION_FILTER_ALL
        if (nm.currentInterruptionFilter != target) {
            nm.setInterruptionFilter(target)
        }
    }

    fun start() {
        if (running) return
        accelerometer?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            running = true
        }
    }

    fun stop() {
        if (!running) return
        sm.unregisterListener(this)
        if (proximityRegistered) {
            sm.unregisterListener(proximityListener)
            proximityRegistered = false
        }
        proximityCovered = false
        isFaceDown       = false
        // Restore DND to all when feature is disabled
        if (nm.isNotificationPolicyAccessGranted &&
            nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        running = false
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}
