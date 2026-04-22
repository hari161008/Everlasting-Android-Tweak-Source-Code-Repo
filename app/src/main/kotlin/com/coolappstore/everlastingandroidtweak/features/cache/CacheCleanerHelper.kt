package com.coolappstore.everlastingandroidtweak.features.cache

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.coolappstore.everlastingandroidtweak.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CacheEntry(
    val packageName: String,
    val appName: String,
    val cacheSizeBytes: Long
)

object CacheCleanerHelper {

    private const val TAG = "CacheCleanerHelper"

    // ── Shizuku state ────────────────────────────────────────────────────────

    fun isShizukuReady(): Boolean = ShizukuManager.isReady()

    fun isShizukuRunningButNotGranted(): Boolean =
        ShizukuManager.isRunning() && !ShizukuManager.hasPermission()

    fun requestShizukuPermissionIfNeeded(requestCode: Int = 1001) =
        ShizukuManager.requestPermission()

    // ── Cache listing ────────────────────────────────────────────────────────

    suspend fun listCacheEntries(context: Context): List<CacheEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = try { pm.getInstalledPackages(0) } catch (_: Exception) { emptyList() }
        val entries = mutableListOf<CacheEntry>()

        packages.forEach { pkg ->
            val name = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg.packageName, 0)).toString()
            } catch (_: Exception) { pkg.packageName }

            val size = if (isShizukuReady())
                getCacheSizeViaShell(pkg.packageName)
            else
                getCacheSizeFallback(context, pkg.packageName)

            entries.add(CacheEntry(pkg.packageName, name, size))
        }

        entries.sortedByDescending { it.cacheSizeBytes }
    }

    // ── Cache clearing ───────────────────────────────────────────────────────

    suspend fun clearCache(context: Context, packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (isShizukuReady())
                clearCacheViaShell(packageName)
            else
                clearCacheFallback(context, packageName)
        }

    suspend fun clearAllCache(context: Context): Int = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = try { pm.getInstalledPackages(0) } catch (_: Exception) { emptyList() }
        var cleared = 0
        packages.forEach { pkg ->
            if (clearCache(context, pkg.packageName)) cleared++
        }
        cleared
    }

    /** Clear cache for a specific list of package names — used by per-app checkbox selection */
    suspend fun clearCacheForPackages(context: Context, packageNames: List<String>): Int =
        withContext(Dispatchers.IO) {
            var cleared = 0
            packageNames.forEach { pkg ->
                if (clearCache(context, pkg)) cleared++
            }
            cleared
        }

    // ── Storage info ─────────────────────────────────────────────────────────

    data class StorageInfo(val totalBytes: Long, val usedBytes: Long, val freeBytes: Long)

    fun getStorageInfo(context: Context): StorageInfo {
        val stat = StatFs(context.filesDir.absolutePath)
        val total = stat.totalBytes
        val free  = stat.freeBytes
        return StorageInfo(total, total - free, free)
    }

    // ── Shizuku shell via Runtime (Shizuku grants elevated exec) ─────────────
    // NOTE: Shizuku.newProcess() was removed in newer API versions.
    // We use Runtime.getRuntime().exec() which inherits the elevated UID
    // when called from within a Shizuku-granted context.

    private fun getCacheSizeViaShell(packageName: String): Long {
        return try {
            val proc = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", "du -sk /data/data/$packageName/cache 2>/dev/null | cut -f1")
            )
            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            (output.toLongOrNull() ?: 0L) * 1024L
        } catch (e: Exception) {
            Log.w(TAG, "getCacheSizeViaShell($packageName): ${e.message}")
            0L
        }
    }

    private fun clearCacheViaShell(packageName: String): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", "pm clear-cache $packageName")
            )
            proc.waitFor()
            proc.exitValue() == 0
        } catch (e: Exception) {
            Log.w(TAG, "clearCacheViaShell($packageName): ${e.message}")
            // Fallback: rm -rf the cache dir
            try {
                val rm = Runtime.getRuntime().exec(
                    arrayOf("sh", "-c", "rm -rf /data/data/$packageName/cache/*")
                )
                rm.waitFor()
                true
            } catch (_: Exception) { false }
        }
    }

    // ── No-root fallback (external cache dirs only) ───────────────────────────

    private fun getCacheSizeFallback(context: Context, packageName: String): Long {
        val extBase = context.getExternalFilesDir(null)
            ?.parentFile?.parentFile?.absolutePath ?: return 0L
        val dir = File("$extBase/$packageName/cache")
        return if (dir.exists()) dirSize(dir) else 0L
    }

    private fun clearCacheFallback(context: Context, packageName: String): Boolean {
        return try {
            val extBase = context.getExternalFilesDir(null)
                ?.parentFile?.parentFile?.absolutePath ?: return false
            val dir = File("$extBase/$packageName/cache")
            if (dir.exists()) deleteDir(dir) else false
        } catch (_: Exception) { false }
    }

    private fun dirSize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
        return size
    }

    private fun deleteDir(dir: File): Boolean {
        dir.walkBottomUp().forEach { it.delete() }
        return !dir.exists()
    }
}
