package com.coolappstore.everlastingandroidtweak.features.appupdater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

data class UpdateResult(
    val packageName: String,
    val appName: String,
    val installedVersion: String,
    val latestVersion: String,
    val downloadUrl: String,
    val hasUpdate: Boolean
)

class AppUpdaterHelper {
    companion object {
        private const val SELF_REPO = "hari161008/Everlasting-Android-Tweak"

        private fun isNewerVersion(latest: String, installed: String): Boolean {
            fun parse(v: String): List<Int> = v.trim().trimStart('v')
                .split(Regex("[.\\-]"))
                .map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val l = parse(latest); val i = parse(installed)
            for (idx in 0 until maxOf(l.size, i.size)) {
                val lv = l.getOrElse(idx) { 0 }; val iv = i.getOrElse(idx) { 0 }
                if (lv > iv) return true; if (lv < iv) return false
            }
            return false
        }

        suspend fun checkSelfUpdate(context: Context): UpdateResult? = withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$SELF_REPO/releases/latest"
                val json = org.json.JSONObject(URL(url).readText())
                val latestTag = json.getString("tag_name").trimStart('v')
                val apkAsset = json.getJSONArray("assets")
                    .let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
                    .firstOrNull { it.getString("name").endsWith(".apk") }
                val downloadUrl = apkAsset?.getString("browser_download_url") ?: json.getString("html_url")
                val pm = context.packageManager
                val installed = try { pm.getPackageInfo(context.packageName, 0).versionName ?: "unknown" } catch (_: Exception) { "unknown" }
                UpdateResult(context.packageName, "Everlasting Android Tweak", installed, latestTag, downloadUrl, hasUpdate = isNewerVersion(latestTag, installed))
            } catch (_: Exception) { null }
        }

        suspend fun checkGitHubApk(repoSlug: String, packageName: String, context: Context): UpdateResult? = withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$repoSlug/releases/latest"
                val json = org.json.JSONObject(URL(url).readText())
                val latestTag = json.getString("tag_name").trimStart('v')
                val apkAsset = json.getJSONArray("assets")
                    .let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
                    .firstOrNull { it.getString("name").endsWith(".apk") }
                val downloadUrl = apkAsset?.getString("browser_download_url") ?: json.getString("html_url")
                val pm = context.packageManager
                val appName = try { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() } catch (_: Exception) { packageName }
                val installed = try { pm.getPackageInfo(packageName, 0).versionName ?: "unknown" } catch (_: Exception) { "not installed" }
                UpdateResult(packageName, appName, installed, latestTag, downloadUrl,
                    hasUpdate = installed != "not installed" && isNewerVersion(latestTag, installed))
            } catch (_: Exception) { null }
        }
    }
}
