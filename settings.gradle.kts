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
include(":seed-vault-sdk")
include(":seed-vault-service")
include(":seed-vault-demo-app")
