package com.coolappstore.everlastingandroidtweak.utils

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

object FlashlightUtil {
    private const val TAG = "FlashlightUtil"

    private fun safeSetTorchMode(cameraManager: CameraManager, cameraId: String, enabled: Boolean): Boolean {
        return try {
            cameraManager.setTorchMode(cameraId, enabled)
            true
        } catch (e: CameraAccessException) {
            if (e.reason == CameraAccessException.CAMERA_IN_USE) return false
            Log.e(TAG, "Failed to set torch mode ($enabled) for camera $cameraId", e)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set torch mode ($enabled) for camera $cameraId", e)
            true
        }
    }

    private fun safeSetTorchStrength(cameraManager: CameraManager, cameraId: String, level: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return try {
            cameraManager.turnOnTorchWithStrengthLevel(cameraId, level)
            true
        } catch (e: CameraAccessException) {
            if (e.reason == CameraAccessException.CAMERA_IN_USE) return false
            Log.e(TAG, "Failed to set torch strength ($level) for camera $cameraId", e)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set torch strength ($level) for camera $cameraId", e)
            true
        }
    }

    fun isIntensitySupported(context: Context, cameraId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val maxLevel = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 0
            maxLevel > 1
        } catch (e: Exception) {
            Log.e(TAG, "Error checking intensity support", e)
            false
        }
    }

    fun getMaxLevel(context: Context, cameraId: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return 1
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
        } catch (e: Exception) { 1 }
    }

    fun getDefaultLevel(context: Context, cameraId: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return 1
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL) ?: 1
        } catch (e: Exception) { 1 }
    }

    fun getCurrentLevel(context: Context, cameraId: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return 1
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            cameraManager.getTorchStrengthLevel(cameraId)
        } catch (e: Exception) { 1 }
    }

    /**
     * Pulse the flashlight a given number of times at the given speed.
     */
    suspend fun pulseFlashlight(context: Context, cameraId: String, count: Int, speedMs: Long) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            repeat(count) {
                safeSetTorchMode(cameraManager, cameraId, true)
                delay(speedMs)
                safeSetTorchMode(cameraManager, cameraId, false)
                delay(speedMs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            try { safeSetTorchMode(cameraManager, cameraId, false) } catch (_: Exception) {}
        }
    }

    /**
     * Smoothly transitions flashlight intensity between two levels.
     */
    suspend fun fadeFlashlight(
        context: Context,
        cameraId: String,
        fromLevel: Int,
        toLevel: Int,
        durationMs: Long = 250L,
        steps: Int = 10
    ): Boolean {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return safeSetTorchMode(cameraManager, cameraId, toLevel > 0)
        }
        val delayPerStep = durationMs / steps
        return try {
            var success = true
            for (i in 1..steps) {
                val level = fromLevel + ((toLevel - fromLevel) * i / steps)
                success = if (level > 0) safeSetTorchStrength(cameraManager, cameraId, level)
                          else if (i == steps) safeSetTorchMode(cameraManager, cameraId, false)
                          else true
                if (!success) return false
                delay(delayPerStep)
            }
            if (toLevel > 0) safeSetTorchStrength(cameraManager, cameraId, toLevel)
            else safeSetTorchMode(cameraManager, cameraId, false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is CameraAccessException || e.reason != CameraAccessException.CAMERA_IN_USE) {
                Log.e(TAG, "Error during flashlight fade", e)
                safeSetTorchMode(cameraManager, cameraId, toLevel > 0)
            } else false
        }
    }

    suspend fun fadeFlashlight(
        context: Context,
        cameraId: String,
        targetOn: Boolean,
        maxLevel: Int = getMaxLevel(context, cameraId),
        durationMs: Long = 400L,
        steps: Int = 20
    ): Boolean {
        val currentLevel = if (targetOn) 0 else getCurrentLevel(context, cameraId)
        val targetLevel  = if (targetOn) maxLevel else 0
        return fadeFlashlight(context, cameraId, currentLevel, targetLevel, durationMs, steps)
    }

    /** Return the first camera ID that has a flash, or null. */
    fun getTorchCameraId(context: Context): String? {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) { null }
    }
}
