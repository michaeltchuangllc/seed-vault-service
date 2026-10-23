# seed-vault-service
Companion Android service for the Seed Vault Trusty applet.

## Modules

- **seedVaultService**: Android Seed Vault app in the Solana Seed Vault style (settings, seed details, authorized apps). Generates and imports 25-word Falcon-1024 seed phrases, stores them in Room (encrypted with an Android Keystore key, or kept in the Trusty applet when available) and exposes a bound AIDL service to authorized apps.
- **seedVaultSDK**: UI-free Android client library for the Falcon-1024 Seed Vault service and the Solana Mobile Seed Vault, including AIDL, account access, networking and payment flows.
- **serviceDemoApp**: Sample wallet that consumes `seedVaultSDK` for its Algorand and Solana tabs.

## Dependencies

`seedVaultService` depends on `com.michaeltchuang.algokit.walletsdk:wallet-sdk-core` (version `walletSdkCore` in `gradle/libs.versions.toml`), resolved from `mavenLocal()` and Maven Central. Until the version that exposes the Falcon-25, encryption and foundation APIs is released, publish it locally from `algokit-walletsdk-kmp`:

```
VERSION_TAG=0.0.2-local ./gradlew :wallet-sdk-core:publishAndroidPublicationToMavenLocal :wallet-sdk-core:publishKotlinMultiplatformPublicationToMavenLocal -PsignAllPublications=false
```

## Build and test

```
./gradlew :seedVaultService:ktlintFormat
./gradlew :seedVaultService:testDebugUnitTest :seedVaultService:ktlintCheck :seedVaultService:assembleDebug :serviceDemoApp:assembleDebug
```

Install `seedVaultService` before `serviceDemoApp` so the permission exists when the client is installed. After a package rename or a Room schema change, uninstall the app first.
