package com.coolappstore.everlastingandroidtweak.features.appupdater.domain.github

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class GitHubRepo(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("description") val description: String?,
    @SerializedName("stargazers_count") val stars: Int,
    @SerializedName("owner") val owner: GitHubOwner
)

@Keep
data class GitHubOwner(
    @SerializedName("login") val login: String,
    @SerializedName("avatar_url") val avatarUrl: String
)

@Keep
data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("published_at") val publishedAt: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("prerelease") val prerelease: Boolean = false,
    @SerializedName("assets") val assets: List<GitHubAsset>
)

@Keep
data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String
)
