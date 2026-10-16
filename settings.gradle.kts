pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "anda-launcher"

include(
    ":app",
    ":core:model",
    ":core:data",
    ":core:apps",
    ":core:icons",
    ":core:ui",
    ":feature:home",
    ":feature:drawer",
    ":feature:search",
    ":feature:gestures",
    ":feature:badges",
    ":feature:settings",
    ":baselineprofile",
)
