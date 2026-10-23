# seed-vault-service
Companion Android service for the Seed Vault Trusty applet.

## Modules

- **seed-vault-service**: Android Seed Vault app in the Solana Seed Vault style (settings, seed details, authorized apps). Generates and imports 25-word Falcon-1024 seed phrases, stores them in Room (encrypted with an Android Keystore key, or kept in the Trusty applet when available) and exposes a bound AIDL service to authorized apps.
- **seed-vault-sdk**: UI-free Android client library for the Falcon-1024 Seed Vault service and the Solana Mobile Seed Vault, including AIDL, account access, networking and payment flows.
- **seed-vault-demo-app**: Sample wallet that consumes `seed-vault-sdk` for its Algorand and Solana tabs.

## Embedded libraries

Both native libraries are checked in as local `.aar` files in `seed-vault-service/libs/` and referenced with `implementation(files("libs/..."))`:

- **`algokit_seed_vault.aar`**: client for the Trusty applet (Falcon-1024 keys in the TEE).
- **`wallet_sdk_core.aar`**: `wallet-sdk-core` from `algokit-walletsdk-kmp` (Falcon-25 signing, Keystore encryption, MVI base classes). 

## Build and test

```
./gradlew :seed-vault-service:ktlintFormat
./gradlew :seed-vault-service:testDebugUnitTest :seed-vault-service:ktlintCheck :seed-vault-service:assembleDebug :seed-vault-demo-app:assembleDebug
```

Install `seed-vault-service` before `seed-vault-demo-app` so the permission exists when the client is installed. After a package rename or a Room schema change, uninstall the app first.

