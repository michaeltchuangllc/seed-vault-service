# seed-vault-service
Companion Android service for the Seed Vault Trusty applet.

## Modules

- **seedVaultService**: Android Seed Vault app in the Solana Seed Vault style (settings, seed details, authorized apps). Generates and imports 25-word Falcon-1024 seed phrases, stores them in Room (encrypted with an Android Keystore key, or kept in the Trusty applet when available) and exposes a bound AIDL service to authorized apps.
- **seedVaultSDK**: UI-free Android client library for the Falcon-1024 Seed Vault service and the Solana Mobile Seed Vault, including AIDL, account access, networking and payment flows.
- **serviceDemoApp**: Sample wallet that consumes `seedVaultSDK` for its Algorand and Solana tabs.

## Embedded libraries

Both native libraries are checked in as local `.aar` files in `seedVaultService/libs/` and referenced with `implementation(files("libs/..."))`:

- **`algokit_seed_vault.aar`**: client for the Trusty applet (Falcon-1024 keys in the TEE).
- **`wallet_sdk_core.aar`**: `wallet-sdk-core` from `algokit-walletsdk-kmp` (Falcon-25 signing, Keystore encryption, MVI base classes). It was built from `main` after `META-INF/*.kotlin_module` was removed from core's packaging excludes. Without that, Kotlin cannot see core's top-level functions, and the `3.202604.0` release on Maven Central has that problem.

A local `.aar` carries no transitive dependencies, so the ones they need at runtime are declared by hand in `seedVaultService/build.gradle.kts` (versions copied from core's published POM). When updating an `.aar`, compare those versions with the new POM.

To refresh `wallet_sdk_core.aar`, publish core to Maven local from `algokit-walletsdk-kmp` and copy the result:

```
VERSION_TAG=0.0.3-local ./gradlew :wallet-sdk-core:publishAndroidPublicationToMavenLocal -PsignAllPublications=false
cp ~/.m2/repository/com/michaeltchuang/algokit/walletsdk/wallet-sdk-core-android/0.0.3-local/wallet-sdk-core-android-0.0.3-local.aar \
   seedVaultService/libs/wallet_sdk_core.aar
```

Once a core release that includes the `kotlin_module` fix is on Maven Central, replace the `.aar` and the hand-declared dependencies with that one coordinate.

## Build and test

```
./gradlew :seedVaultService:ktlintFormat
./gradlew :seedVaultService:testDebugUnitTest :seedVaultService:ktlintCheck :seedVaultService:assembleDebug :serviceDemoApp:assembleDebug
```

Install `seedVaultService` before `serviceDemoApp` so the permission exists when the client is installed. After a package rename or a Room schema change, uninstall the app first.

## CI and releases

Workflows in `.github/workflows/`:

- **`android-build.yml`**: on every push and PR (and nightly), runs `testDebugUnitTest`, `ktlintCheck` + `lintDebug`, and `assembleDebug` for `seedVaultService`, `seedVaultSDK` and `serviceDemoApp`.
- **`publish-sdk-maven.yml`**: on a `vX.Y.Z` tag, publishes `seedVaultSDK` to Maven Central as `com.michaeltchuang.algokit.seedvault:seed-vault-sdk:X.Y.Z` (staged, release it manually in the Central portal).
- **`publish-app-play-store.yml`**: on a `vX.Y.Z` tag or manual run, builds the signed `seedVaultService` bundle and uploads it to the Play Store internal track. `versionName` is the tag, `versionCode` is the run number plus an offset.

Repository secrets: `RSA_GPG_PRIVATE_KEY`, `RSA_GPG_PASSPHRASE`, `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` (Maven Central) and `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `PLAY_STORE_SERVICE_ACCOUNT_JSON` (Play Store). For local release builds put `keystore.jks` in the repo root and `keystore.password`, `key.alias`, `key.password` in `local.properties`.

Both publish workflows fire on the same tag. The first Play Store upload of `com.michaeltchuang.seedvault.service` needs the app to exist in the Play Console first.
