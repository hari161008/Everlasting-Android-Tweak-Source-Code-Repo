pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack – only queried for artifacts that actually live there.
        // Content filtering prevents jitpack network errors from blocking
        // resolution of unrelated dependencies (like atomicfu from MavenCentral).
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("dev.zwander")
                includeGroupByRegex("com\\.github\\..*")
                includeGroup("com.joaomgcd")
            }
        }
    }
}

rootProject.name = "EverlastingAndroidTweak"
include(":app")
