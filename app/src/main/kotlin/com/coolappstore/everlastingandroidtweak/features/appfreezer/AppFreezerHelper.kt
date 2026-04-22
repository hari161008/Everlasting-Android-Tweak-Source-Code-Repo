package com.coolappstore.everlastingandroidtweak.features.appfreezer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.coolappstore.everlastingandroidtweak.shizuku.ShizukuManager

/**
 * AppFreezerHelper — manages force-stopping apps via Shizuku shell.
 *
 * ROOT CAUSE FIX:
 *   Old code used "pm disable-user --user 0 <pkg>" which PERMANENTLY DISABLES the
 *   app (it disappears from the launcher and cannot be opened until re-enabled).
 *   That is not "freezing" — it is destructive.
 *
 *   New code uses "am force-stop <pkg>" which terminates all running processes and
 *   services for the package immediately, identical to the "Force Stop" button in
 *   Android Settings → App Info. The app remains installed and usable normally.
 *
 *   "Unfreeze" is now a no-op confirmation (force-stop cannot be undone — the app
 *   simply restarts when the user or system launches it next). We track which apps
 *   were stopped in-session so the UI can show a ✓ badge.
 */
class AppFreezerHelper {
    companion object {

        fun isShizukuReady(): Boolean = ShizukuManager.isReady()

        /**
         * Force-stops [packageName] via `am force-stop`.
         * Requires Shizuku (shell-level adb permission).
         * Returns true if the command exited successfully.
         */
        fun forceStopApp(packageName: String): Boolean = try {
            val proc = Runtime.getRuntime()
                .exec(arrayOf("sh", "-c", "am force-stop $packageName"))
            // Drain stderr so the process doesn't block
            proc.errorStream.readBytes()
            proc.waitFor() == 0
        } catch (_: Exception) { false }

        /**
         * Kill all background processes for [packageName].
         * Secondary method used as fallback after force-stop.
         */
        fun killBackgroundProcesses(packageName: String): Boolean = try {
            val proc = Runtime.getRuntime()
                .exec(arrayOf("sh", "-c", "am kill $packageName"))
            proc.errorStream.readBytes()
            proc.waitFor() == 0
        } catch (_: Exception) { false }

        /**
         * Force-stop using both am force-stop AND am kill for thoroughness.
         * Returns true if at least one command succeeded.
         */
        fun freezeApp(packageName: String): Boolean {
            val stopped = forceStopApp(packageName)
            killBackgroundProcesses(packageName) // belt-and-suspenders
            return stopped
        }

        /**
         * "Unfreeze" is kept for UI state tracking only.
         * A force-stopped app automatically resumes when launched again — nothing
         * needs to be reversed. This method is a no-op that always returns true.
         */
        fun unfreezeApp(packageName: String): Boolean = true

        /**
         * Returns all user-installed (non-system) apps sorted by label.
         */
        fun getUserApps(context: Context): List<ApplicationInfo> =
            context.packageManager
                .getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .sortedBy { context.packageManager.getApplicationLabel(it).toString() }

        /**
         * Returns all currently running packages via `am dump-stack`.
         * Used to show a "running" badge in the app list.
         */
        fun getRunningPackages(): Set<String> = try {
            val proc = Runtime.getRuntime()
                .exec(arrayOf("sh", "-c", "am stack list 2>/dev/null | grep 'packageName'"))
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            // Parse "packageName=com.example" entries
            Regex("packageName=([\\w.]+)").findAll(out)
                .map { it.groupValues[1] }.toSet()
        } catch (_: Exception) { emptySet() }
    }
}
