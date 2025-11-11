// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://plugins.gradle.org/m2/")
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // Important: repos come from SETTINGS, not the build file
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }
}

rootProject.name = "modnvote"
