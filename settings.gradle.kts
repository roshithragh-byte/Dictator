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

rootProject.name = "FieldFlow"
include(":app")
include(":core:audio")
include(":core:overlay")
include(":core:stt")
include(":core:watcher")
include(":core:data")
include(":core:transform")
include(":core:appfunctions")
include(":core:export")
