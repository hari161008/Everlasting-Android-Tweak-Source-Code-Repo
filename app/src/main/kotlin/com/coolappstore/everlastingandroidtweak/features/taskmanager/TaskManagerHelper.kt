package com.coolappstore.everlastingandroidtweak.features.taskmanager

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppProcess(
    val packageName: String,
    val appName: String,
    val pid: Int,
    val uid: Int,
    val importance: Int,
    val isSystem: Boolean,
    val memoryKb: Long,
    val isRunning: Boolean
)

object TaskManagerHelper {

    /**
     * Returns ALL installed user apps combined with running process info.
     * System apps are included if [includeSystem] is true.
     *
     * This works on Android 12+ (minSdk=31) without needing root.
     */
    suspend fun getApps(
        context: Context,
        includeSystem: Boolean = false
    ): List<AppProcess> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // ── 1. Get ALL running processes (not just own) ───────────────────────
        val runningProcs: List<ActivityManager.RunningAppProcessInfo> = try {
            am.runningAppProcesses ?: emptyList()
        } catch (_: Exception) { emptyList() }

        // Build a map: packageName → process info
        val procMap = mutableMapOf<String, ActivityManager.RunningAppProcessInfo>()
        runningProcs.forEach { proc ->
            proc.pkgList?.forEach { pkg -> procMap[pkg] = proc }
        }

        // ── 2. Get memory stats for running pids ──────────────────────────────
        val pids = runningProcs.map { it.pid }.toIntArray()
        val memMap = mutableMapOf<Int, Long>()
        if (pids.isNotEmpty()) {
            try {
                am.getProcessMemoryInfo(pids).forEachIndexed { i, info ->
                    memMap[pids[i]] = info.totalPss.toLong()
                }
            } catch (_: Exception) {}
        }

        // ── 3. Enumerate ALL installed apps ───────────────────────────────────
        val installedApps: List<ApplicationInfo> = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Exception) { emptyList() }

        // ── 4. Merge ──────────────────────────────────────────────────────────
        val result = mutableListOf<AppProcess>()

        installedApps.forEach { appInfo ->
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                           (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            if (!includeSystem && isSystem) return@forEach
            // Skip the launcher itself and our own app
            if (appInfo.packageName == context.packageName) return@forEach

            val proc     = procMap[appInfo.packageName]
            val pid      = proc?.pid ?: -1
            val memKb    = if (pid > 0) memMap[pid] ?: 0L else 0L
            val isRunning = proc != null

            val appName = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) { appInfo.packageName }

            result.add(
                AppProcess(
                    packageName  = appInfo.packageName,
                    appName      = appName,
                    pid          = pid,
                    uid          = appInfo.uid,
                    importance   = proc?.importance ?: 0,
                    isSystem     = isSystem,
                    memoryKb     = memKb,
                    isRunning    = isRunning
                )
            )
        }

        // ── 5. Sort: running first, then by memory usage descending ───────────
        result.sortWith(compareByDescending<AppProcess> { it.isRunning }
            .thenByDescending { it.memoryKb })
        result
    }

    /**
     * Kill an app's process. Requires the process UID to match or root.
     * On non-rooted devices this can only kill processes with the same UID (our own).
     * For other apps it sends a graceful signal via ActivityManager.killBackgroundProcesses
     * which requires KILL_BACKGROUND_PROCESSES permission.
     */
    suspend fun killApp(context: Context, packageName: String) = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try {
            am.killBackgroundProcesses(packageName)
        } catch (e: Exception) { e.printStackTrace() }
    }

    /**
     * Returns total RAM info: [totalMb, availableMb, usedMb].
     */
    fun getRamInfo(context: Context): Triple<Long, Long, Long> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val totalMb = info.totalMem / 1024 / 1024
        val availMb = info.availMem / 1024 / 1024
        return Triple(totalMb, availMb, totalMb - availMb)
    }
}
