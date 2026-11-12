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
        // Pleos Connect SDK. Only asked for the SDK's own artifacts, and only when the "pleos"
        // flavor is switched on in gradle.properties.
        maven {
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
            content {
                includeGroup("ai.pleos.playground")
            }
        }
    }
}

rootProject.name = "gildongmu"
include(":app")
