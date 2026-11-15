pluginManagement {
    includeBuild("build-logic")
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
    }
}

rootProject.name = "KeyHack"

// Core IME app and its libraries. None of these may pull in INTERNET; the
// :app permission guard enforces it on the merged release manifest.
include(
    ":app",
    ":ime",
    ":keyboard",
    ":layouts",
    ":suggest",
    ":emoji",
    ":stickers",
    ":commit",
    ":devtools",
    ":clipboard",
    ":security",
    ":data",
    ":theme",
    ":core",
    ":online-api",
)

// KeyHack Online, the optional networked companion. It never hosts an IME.
include(":companion")
project(":companion").projectDir = file("companion")
