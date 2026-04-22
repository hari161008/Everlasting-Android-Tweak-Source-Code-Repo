package com.coolappstore.everlastingandroidtweak.features.appupdater.data

import android.content.Context
import com.google.gson.Gson
import com.coolappstore.everlastingandroidtweak.features.appupdater.domain.TrackedRepo

class AppUpdatesStorage(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        const val PREFS_NAME = "everlasting_app_updater"
        const val KEY_TRACKED_REPOS = "tracked_repos"
        const val KEY_GITHUB_TOKEN = "github_token"
    }

    fun getTrackedRepos(): List<TrackedRepo> {
        val json = prefs.getString(KEY_TRACKED_REPOS, null) ?: return emptyList()
        return try {
            gson.fromJson(json, Array<TrackedRepo>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTrackedRepos(repos: List<TrackedRepo>) {
        prefs.edit().putString(KEY_TRACKED_REPOS, gson.toJson(repos)).apply()
    }

    fun addOrUpdateTrackedRepo(repo: TrackedRepo) {
        val current = getTrackedRepos().toMutableList()
        val index = current.indexOfFirst { it.fullName == repo.fullName }
        if (index != -1) current[index] = repo else current.add(repo)
        saveTrackedRepos(current)
    }

    fun removeTrackedRepo(fullName: String) {
        val current = getTrackedRepos().toMutableList()
        current.removeAll { it.fullName == fullName }
        saveTrackedRepos(current)
    }

    fun getGitHubToken(): String? = prefs.getString(KEY_GITHUB_TOKEN, null)

    fun saveGitHubToken(token: String?) =
        prefs.edit().putString(KEY_GITHUB_TOKEN, token).apply()
}
