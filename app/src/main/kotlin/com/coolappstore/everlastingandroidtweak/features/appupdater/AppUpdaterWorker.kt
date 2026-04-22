package com.coolappstore.everlastingandroidtweak.features.appupdater

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.coolappstore.everlastingandroidtweak.features.appupdater.data.AppUpdatesStorage
import com.coolappstore.everlastingandroidtweak.features.appupdater.data.GitHubRepository

class AppUpdaterWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val storage = AppUpdatesStorage(applicationContext)
            val repos = storage.getTrackedRepos()
            if (repos.isEmpty()) return Result.success()

            val gitHubRepository = GitHubRepository()
            val token = storage.getGitHubToken()
            val updatedRepos = repos.toMutableList()
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
                        val newRepo = repo.copy(
                            latestTagName    = release.tagName,
                            latestReleaseName = release.name,
                            latestReleaseBody = release.body,
                            latestReleaseUrl  = release.htmlUrl,
                            downloadUrl = release.assets
                                .find { it.name == repo.selectedApkName }?.downloadUrl
                                ?: release.assets.firstOrNull { it.name.endsWith(".apk") }?.downloadUrl,
                            publishedAt = release.publishedAt
                        )
                        if (newRepo != repo) {
                            updatedRepos[i] = newRepo
                            changesMade = true
                        }
                    }
                } catch (_: Exception) { /* skip this repo on network error */ }
            }

            if (changesMade) {
                storage.saveTrackedRepos(updatedRepos)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
