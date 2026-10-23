import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.gradle.play.publisher)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.room)
}

android {
    namespace = "com.michaeltchuang.seedvault.service"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.michaeltchuang.seedvault.service"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0) +
            libs.versions.android.versionCode.offset
                .get()
                .toInt()
        versionName = System.getenv("VERSION_TAG") ?: "0.0.1"
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { stream -> localProperties.load(stream) }
    }

    val keystoreFile = rootProject.file("keystore.jks").takeIf { it.exists() }

    signingConfigs {
        create("release") {
            storeFile = keystoreFile
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: localProperties.getProperty("keystore.password")
            keyAlias = System.getenv("KEY_ALIAS") ?: localProperties.getProperty("key.alias")
            keyPassword = System.getenv("KEY_PASSWORD") ?: localProperties.getProperty("key.password")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        aidl = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Same duplicate META-INF files as androidDemoApp, pulled in through wallet-sdk-core
    packaging {
        resources {
            excludes +=
                listOf(
                    "META-INF/DEPENDENCIES.md",
                    "META-INF/NOTICE.md",
                    "META-INF/LICENSE.md",
                    "META-INF/LICENSE.txt",
                    "META-INF/NOTICE.txt",
                    "META-INF/ASL2.0.md",
                    "META-INF/DEPENDENCIES",
                    "META-INF/LICENSE",
                    "META-INF/license.txt",
                    "META-INF/NOTICE",
                    "META-INF/notice.txt",
                    "META-INF/ASL2.0",
                    "META-INF/*.kotlin_module",
                    // Desktop-only native libs shipped as resources, rejected by bundletool
                    "lib/libnarcissus-*",
                )
            pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

play {
    serviceAccountCredentials.set(rootProject.file("service-account.json"))
    track.set("internal")
    defaultToAppBundles.set(true)
}

dependencies {
    // Falcon-1024 seed phrases and Android Keystore encryption
    // wallet-sdk-core, built from algokit-walletsdk-kmp. Replace with the Maven coordinate once a release includes
    // META-INF/*.kotlin_module. A local aar carries no transitive dependencies, so its runtime ones are declared here.
    implementation(files("libs/wallet_sdk_core.aar"))
    implementation("io.github.aakira:napier-android:2.7.1")
    // algokit-core Rust FFI, built locally: algokit-crypto has the Falcon-1024 derivation (salted addresses) and
    // algokit-transact the pqsig signed transaction, which the Maven alpha.85 builds lack. They replace the Maven
    // coordinates wallet-sdk-core was built against, so no other copy of these libraries may be on the classpath.
    implementation(files("libs/algokit_composer.aar"))
    implementation(files("libs/algokit_crypto.aar"))
    implementation(files("libs/algokit_transact.aar"))
    // Only used by the parts of wallet-sdk-core the vault does not call (the vault uses the Rust FFI above)
    implementation("io.github.algorandecosystem:falcon-signatures-mobile-sdk:0.0.19")
    implementation("cash.z.ecc.android:kotlin-bip39-jvm:1.0.9")
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.11.0")
    implementation("javax.inject:javax.inject:1")
    implementation("com.appmattus.fixture:fixture:1.2.0")
    implementation("com.michaeltchuang.algokit:algorand-foundation-crypto:0.1.0")
    implementation("com.michaeltchuang.algokit:algorand-foundation-provider:0.1.0")
    implementation("app.perawallet:dP256:1.0.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.22")
    implementation("org.msgpack:jackson-dataformat-msgpack:0.9.12")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.22.1")
    implementation("net.pwall.json:json-kotlin-schema:0.48")
    implementation("io.github.erdtman:java-json-canonicalization:1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.8.0")
    implementation("com.ionspin.kotlin:bignum-jvm:0.3.10")
    implementation(libs.kotlinx.coroutines.android)
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)

    // Trusty applet client (Falcon-1024 keys in the TEE). Replace with the Maven coordinate once published.
    // A local aar carries no transitive dependencies, so its JNA runtime is declared here.
    implementation(files("libs/algokit_seed_vault.aar"))
    implementation("net.java.dev.jna:jna:5.17.0@aar")

    // JSON returned over AIDL
    implementation(libs.kotlinx.serialization.json)

    // Seed storage
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activityCompose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.biometric)
    debugImplementation(libs.compose.ui.tooling)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
