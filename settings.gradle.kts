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

// One app: the keyboard and all its features. Only :media touches the
// network (GIF search), and only after the user opens the GIF panel.
include(
    ":app",
    ":ime",
    ":keyboard",
    ":layouts",
    ":suggest",
    ":emoji",
    ":media",
    ":stickers",
    ":commit",
    ":devtools",
    ":clipboard",
    ":security",
    ":data",
    ":theme",
    ":core",
)
