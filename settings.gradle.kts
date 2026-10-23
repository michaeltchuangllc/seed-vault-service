pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "seed-vault-service"
include(":seedVaultSDK")
include(":seedVaultService")
include(":serviceDemoApp")
