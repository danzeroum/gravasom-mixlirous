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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "gravasom"

includeBuild("build-logic")

include(":app")
include(":feature:record")
include(":feature:library")
include(":feature:player")
include(":feature:settings")
include(":feature:enhance")
include(":feature:criar")
include(":core:domain")
include(":core:audio-engine")
include(":core:audio-android")
include(":core:dsp")
include(":core:mixengine")
include(":core:data")
include(":core:service")
include(":core:ui")
include(":baselineprofile")
include(":macrobenchmark")
// Spike de transcricao (ADR-013): fora do build normal; entra com -Pspike.
if (providers.gradleProperty("spike").isPresent) include(":spike:whisper")
