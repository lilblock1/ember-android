pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "AndroidJavaPort"
include(":app", ":core", ":authentication", ":native-engine")
include(":compatibility-lab")
include(":lwjgl-compat")
