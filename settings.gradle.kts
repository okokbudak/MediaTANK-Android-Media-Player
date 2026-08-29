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
        // libVLC
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    }
}
rootProject.name = "BraviaIPTV"
include(":app")
