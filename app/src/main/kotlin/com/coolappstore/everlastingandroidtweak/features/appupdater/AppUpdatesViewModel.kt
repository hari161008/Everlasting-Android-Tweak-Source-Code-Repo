package com.coolappstore.everlastingandroidtweak.features.appupdater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolappstore.everlastingandroidtweak.features.appupdater.data.AppUpdatesStorage
import com.coolappstore.everlastingandroidtweak.features.appupdater.data.GitHubRepository
import com.coolappstore.everlastingandroidtweak.features.appupdater.domain.TrackedRepo
import com.coolappstore.everlastingandroidtweak.features.appupdater.domain.github.GitHubRelease
import com.coolappstore.everlastingandroidtweak.features.appupdater.domain.github.GitHubRepo
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class AppUpdatesViewModel : ViewModel() {

    private val gitHubRepository = GitHubRepository()
    private val gson = Gson()

    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    private val _isSearching = mutableStateOf(false)
    val isSearching: State<Boolean> = _isSearching

    private val _searchResult = mutableStateOf<GitHubRepo?>(null)
    val searchResult: State<GitHubRepo?> = _searchResult

    private val _latestRelease = mutableStateOf<GitHubRelease?>(null)
    val latestRelease: State<GitHubRelease?> = _latestRelease

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage

    private val _shouldDismissSheet = mutableStateOf(false)
    val shouldDismissSheet: State<Boolean> = _shouldDismissSheet

    private val _readmeContent = mutableStateOf<String?>(null)
    val readmeContent: State<String?> = _readmeContent

    private val _trackedRepos = mutableStateOf<List<TrackedRepo>>(emptyList())
    val trackedRepos: State<List<TrackedRepo>> = _trackedRepos

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _refreshingRepoIds = mutableStateOf<Set<String>>(emptySet())
    val refreshingRepoIds: State<Set<String>> = _refreshingRepoIds

    private val _updateProgress = mutableStateOf(0f)
    val updateProgress: State<Float> = _updateProgress

    private val _allowPreReleases = mutableStateOf(false)
    val allowPreReleases: State<Boolean> = _allowPreReleases

    private val _installingRepoId = mutableStateOf<String?>(null)
    val installingRepoId: State<String?> = _installingRepoId

    private val _installStatus = mutableStateOf<String?>(null)
    val installStatus: State<String?> = _installStatus

    // For APK selection when a repo has multiple APKs
    private val _availableApks = mutableStateOf<List<String>>(emptyList())
    val availableApks: State<List<String>> = _availableApks

    private val _selectedApkName = mutableStateOf("Auto")
    val selectedApkName: State<String> = _selectedApkName

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _errorMessage.value = null
    }

    fun loadTrackedRepos(context: Context) {
        _isLoading.value = true
        viewModelScope.launch {
            _trackedRepos.value = AppUpdatesStorage(context).getTrackedRepos()
            _isLoading.value = false
        }
    }

    fun searchRepo(context: Context) {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) return

        val parts = parseRepoQuery(query)
        if (parts == null) {
            _errorMessage.value = "Invalid repo format. Use 'owner/repo' or paste a GitHub URL."
            return
        }

        val (owner, repo) = parts
        _isSearching.value = true
        _errorMessage.value = null
        _searchResult.value = null
        _latestRelease.value = null
        _readmeContent.value = null
        _availableApks.value = emptyList()
        _selectedApkName.value = "Auto"

        viewModelScope.launch {
            try {
                val token = AppUpdatesStorage(context).getGitHubToken()
                val repoInfo = gitHubRepository.getRepoInfo(owner, repo, token)
                if (repoInfo == null) {
                    _errorMessage.value = "Repository not found."
                } else {
                    var release = gitHubRepository.getLatestRelease(owner, repo, token)
                    var isPreRelease = false

                    if (release == null) {
                        val releases = gitHubRepository.getReleases(owner, repo, token)
                        release = releases.firstOrNull()
                        if (release != null) isPreRelease = true
                    }

                    if (release == null || !release.assets.any { it.name.endsWith(".apk") }) {
                        _errorMessage.value = "No APK found in latest release."
                    } else {
                        _searchResult.value = repoInfo
                        _latestRelease.value = release
                        _readmeContent.value = gitHubRepository.getReadme(owner, repo, token)
                        _availableApks.value = release.assets.filter { it.name.endsWith(".apk") }.map { it.name }

                        if (isPreRelease || release.prerelease) {
                            _allowPreReleases.value = true
                        }
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = if (e.message == "RATE_LIMIT")
                    "GitHub rate limit reached. Try again later or add a token."
                else "Search failed. Check your connection."
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun setSelectedApk(name: String) {
        _selectedApkName.value = name
    }

    fun trackRepo(context: Context) {
        val repo = _searchResult.value ?: return
        val release = _latestRelease.value ?: return
        val storage = AppUpdatesStorage(context)

        val apkName = if (_selectedApkName.value == "Auto") {
            release.assets.firstOrNull { it.name.endsWith(".apk") }?.name ?: "Auto"
        } else _selectedApkName.value

        val downloadUrl = release.assets.find { it.name == apkName }?.downloadUrl
            ?: release.assets.firstOrNull { it.name.endsWith(".apk") }?.downloadUrl

        // Try to find installed app matching repo name
        val mappedPackageName = findInstalledPackage(context, repo.name)
        val mappedAppName = mappedPackageName?.let { pkg ->
            try { context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) { null }
        }

        val trackedRepo = TrackedRepo(
            owner = repo.owner.login,
            name = repo.name,
            fullName = repo.fullName,
            description = repo.description,
            stars = repo.stars,
            avatarUrl = repo.owner.avatarUrl,
            latestTagName = release.tagName,
            latestReleaseName = release.name,
            latestReleaseBody = release.body,
            latestReleaseUrl = release.htmlUrl,
            downloadUrl = downloadUrl,
            publishedAt = release.publishedAt,
            selectedApkName = apkName,
            mappedPackageName = mappedPackageName,
            mappedAppName = mappedAppName,
            allowPreReleases = _allowPreReleases.value
        )

        storage.addOrUpdateTrackedRepo(trackedRepo)
        loadTrackedRepos(context)
        clearSearch()
    }

    fun untrackRepo(context: Context, fullName: String) {
        AppUpdatesStorage(context).removeTrackedRepo(fullName)
        loadTrackedRepos(context)
    }

    fun checkForUpdates(context: Context) {
        if (_trackedRepos.value.isEmpty()) return

        viewModelScope.launch {
            val reposToCheck = _trackedRepos.value
            _refreshingRepoIds.value = reposToCheck.map { it.fullName }.toSet()
            _updateProgress.value = 0f
            var completedCount = 0

            val storage = AppUpdatesStorage(context)
            val token = storage.getGitHubToken()
            val updatedRepos = reposToCheck.toMutableList()
            var changesMade = false

            for (i in updatedRepos.indices) {
                val repo = updatedRepos[i]
                try {
                    val release = if (repo.allowPreReleases) {
                        gitHubRepository.getReleases(repo.owner, repo.name, token).firstOrNull()
                    } else {
                        gitHubRepository.getLatestRelease(repo.owner, repo.name, token)
                    }

                    if (release != null) {
                        val isUpdateAvailable = if (repo.mappedPackageName != null) {
                            val installed = getAppVersion(context, repo.mappedPackageName)
                            if (installed != null) compareVersions(release.tagName, installed) > 0 else false
                        } else false

                        val newRepo = repo.copy(
                            latestTagName = release.tagName,
                            latestReleaseName = release.name,
                            latestReleaseBody = release.body,
                            latestReleaseUrl = release.htmlUrl,
                            downloadUrl = release.assets.find { it.name == repo.selectedApkName }?.downloadUrl
                                ?: release.assets.firstOrNull { it.name.endsWith(".apk") }?.downloadUrl,
                            publishedAt = release.publishedAt,
                            isUpdateAvailable = isUpdateAvailable,
                            lastETag = null
                        )

                        if (newRepo != repo) {
                            updatedRepos[i] = newRepo
                            changesMade = true
                        }
                    }
                } catch (e: Exception) {
                    if (e.message == "RATE_LIMIT") {
                        _errorMessage.value = "GitHub rate limit reached."
                        break
                    }
                } finally {
                    _refreshingRepoIds.value = _refreshingRepoIds.value - repo.fullName
                    completedCount++
                    _updateProgress.value = completedCount.toFloat() / reposToCheck.size
                }
            }

            if (changesMade) {
                storage.saveTrackedRepos(updatedRepos)
                _trackedRepos.value = updatedRepos
            }
        }
    }

    fun fetchReleaseNotesIfNeeded(context: Context, repo: TrackedRepo) {
        if (!repo.latestReleaseBody.isNullOrBlank()) return

        viewModelScope.launch {
            try {
                val token = AppUpdatesStorage(context).getGitHubToken()
                val release = if (repo.allowPreReleases) {
                    gitHubRepository.getReleases(repo.owner, repo.name, token).firstOrNull()
                } else {
                    gitHubRepository.getLatestRelease(repo.owner, repo.name, token)
                }

                if (release != null) {
                    val updatedRepo = repo.copy(
                        latestTagName = release.tagName,
                        latestReleaseName = release.name,
                        latestReleaseBody = release.body,
                        latestReleaseUrl = release.htmlUrl,
                        downloadUrl = release.assets.find { it.name == repo.selectedApkName }?.downloadUrl
                            ?: release.assets.firstOrNull { it.name.endsWith(".apk") }?.downloadUrl,
                        publishedAt = release.publishedAt
                    )
                    AppUpdatesStorage(context).addOrUpdateTrackedRepo(updatedRepo)
                    loadTrackedRepos(context)
                }
            } catch (_: Exception) {}
        }
    }

    fun downloadAndInstall(context: Context, repo: TrackedRepo) {
        val downloadUrl = repo.downloadUrl ?: return
        _installingRepoId.value = repo.fullName
        _installStatus.value = "Downloading..."
        _updateProgress.value = 0f

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = context.externalCacheDir ?: context.cacheDir
                val file = File(cacheDir, "${repo.name}.apk")
                file.parentFile?.mkdirs()

                val connection = URL(downloadUrl).openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Server returned ${connection.responseCode}")
                }

                val fileLength = connection.contentLength
                val input = connection.inputStream
                val output = FileOutputStream(file)
                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) _updateProgress.value = total.toFloat() / fileLength
                    output.write(data, 0, count)
                }
                output.flush(); output.close(); input.close()

                withContext(Dispatchers.Main) { installApk(context, file) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Download failed: ${e.message}"
                    _installingRepoId.value = null
                    _installStatus.value = null
                }
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        _installStatus.value = "Installing..."
        try {
            val apkUri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _errorMessage.value = "Install failed: ${e.message}"
        } finally {
            _installingRepoId.value = null
            _installStatus.value = null
        }
    }

    fun exportTrackedRepos(context: Context, outputStream: OutputStream) {
        try {
            val repos = AppUpdatesStorage(context).getTrackedRepos()
            outputStream.write(gson.toJson(repos).toByteArray())
            outputStream.flush()
        } catch (_: Exception) {
        } finally {
            try { outputStream.close() } catch (_: Exception) {}
        }
    }

    fun importTrackedRepos(context: Context, inputStream: InputStream): Boolean {
        return try {
            val json = inputStream.bufferedReader().use { it.readText() }
            val imported = gson.fromJson(json, Array<TrackedRepo>::class.java).toList()
            if (imported.isNotEmpty()) {
                val storage = AppUpdatesStorage(context)
                val current = storage.getTrackedRepos().toMutableList()
                imported.forEach { imp ->
                    val idx = current.indexOfFirst { it.fullName == imp.fullName }
                    if (idx != -1) current[idx] = imp else current.add(imp)
                }
                storage.saveTrackedRepos(current)
                loadTrackedRepos(context)
                true
            } else false
        } catch (_: Exception) { false
        } finally {
            try { inputStream.close() } catch (_: Exception) {}
        }
    }

    fun setAllowPreReleases(allow: Boolean) { _allowPreReleases.value = allow }
    fun clearSearch() {
        _searchQuery.value = ""; _searchResult.value = null; _latestRelease.value = null
        _errorMessage.value = null; _readmeContent.value = null
        _allowPreReleases.value = false; _availableApks.value = emptyList()
        _selectedApkName.value = "Auto"
    }
    fun clearError() { _errorMessage.value = null }
    fun consumeDismissSignal() { _shouldDismissSheet.value = false }

    private fun parseRepoQuery(query: String): Pair<String, String>? {
        val urlPattern = Regex("(?:https?://)?(?:www\\.)?github\\.com/([^/]+)/([^/\\s?#]+).*")
        val urlMatch = urlPattern.matchEntire(query)
        if (urlMatch != null) return urlMatch.groupValues[1] to urlMatch.groupValues[2]
        val simplePattern = Regex("([^/\\s]+)/([^/\\s]+)")
        val simpleMatch = simplePattern.matchEntire(query)
        if (simpleMatch != null) return simpleMatch.groupValues[1] to simpleMatch.groupValues[2]
        return null
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val clean1 = v1.replace(Regex("[^0-9.]"), "").split(".")
        val clean2 = v2.replace(Regex("[^0-9.]"), "").split(".")
        val length = maxOf(clean1.size, clean2.size)
        for (i in 0 until length) {
            val n1 = clean1.getOrNull(i)?.toIntOrNull() ?: 0
            val n2 = clean2.getOrNull(i)?.toIntOrNull() ?: 0
            if (n1 > n2) return 1
            if (n1 < n2) return -1
        }
        return 0
    }

    private fun getAppVersion(context: Context, packageName: String): String? = try {
        context.packageManager.getPackageInfo(packageName, 0).versionName
    } catch (_: Exception) { null }

    private fun findInstalledPackage(context: Context, repoName: String): String? {
        val pm = context.packageManager
        val normalized = repoName.lowercase().replace("-", "").replace("_", "")
        return try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .firstOrNull { info ->
                    val label = pm.getApplicationLabel(info).toString()
                        .lowercase().replace(" ", "").replace("-", "").replace("_", "")
                    label == normalized || label.contains(normalized) || normalized.contains(label)
                }?.packageName
        } catch (_: Exception) { null }
    }
}
