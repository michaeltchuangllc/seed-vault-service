import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.michaeltchuang.seedvault.sdk"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    buildFeatures {
        aidl = true
    }
}

dependencies {
    // algokit-composer pulls JNA 5.14.0, whose x86_64 libjnidispatch.so is not 16 KB aligned.
    implementation(libs.algokit.composer) {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    implementation("net.java.dev.jna:jna:5.17.0@aar")
    implementation(libs.seedvault.wallet.sdk)
    implementation(libs.sol4k)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.kotlinx.serialization)
    implementation(libs.ktor.client.logging)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

mavenPublishing {
    coordinates(
        "com.michaeltchuang.algokit.seedvault",
        "seed-vault-sdk",
        System.getenv("VERSION_TAG") ?: "0.0.1",
    )

    pom {
        name.set("Seed Vault SDK")
        description.set("Android client library for the Falcon-1024 Seed Vault service and the Solana Mobile Seed Vault")
        url.set("https://github.com/michaeltchuangllc/seed-vault-service")

        licenses {
            license {
                name.set("GPL-3.0 License")
                url.set("https://opensource.org/licenses/GPL-3.0")
            }
        }

        developers {
            developer {
                id.set("michaeltchuang")
                name.set("Michael T Chuang")
                email.set("hello@michaeltchuang.com")
                organization.set("Michael T Chuang LLC")
                organizationUrl.set("https://github.com/michaeltchuangllc")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/michaeltchuangllc/seed-vault-service.git")
            developerConnection.set("scm:git:ssh://github.com/michaeltchuangllc/seed-vault-service.git")
            url.set("https://github.com/michaeltchuangllc/seed-vault-service")
        }
    }
}
