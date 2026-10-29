---
name: seed-vault-conventions
description: Conventions for the Seed Vault app (seed-vault-service), its client library (seed-vault-sdk) and its sample client (seed-vault-demo-app). Use before changing seed storage, the SeedKeyVault Room/Trusty abstraction, the AIDL service or its client, app authorization, the Falcon-1024 signing paths, the local .aar libraries, the SDK's public API or Maven publishing, or the demo client's Algorand/Solana tabs.
---

# Seed Vault conventions

Clean architecture, MVI with `StateDelegate`/`EventDelegate` (from `wallet-sdk-core`), Compose, tests and ktlint. Follow the patterns already in the code.

## Modules

| Module | What it is |
|---|---|
| `seed-vault-service` | Android-only Seed Vault app (`com.michaeltchuang.seedvault.service`). Create/import 25-word Falcon-1024 seeds, view the phrase, authorize apps, bound AIDL service. Own theme (`SeedVaultTheme`). |
| `seed-vault-sdk` | UI-free Android client library (`com.michaeltchuang.seedvault.sdk`), published to Maven Central as `com.michaeltchuang.algokit.seedvault:seed-vault-sdk`. Holds the AIDL copy, the service and Solana clients (`client/`), the Algorand and Solana repositories (`data/repository`) and their models. |
| `seed-vault-demo-app` | Android-only sample wallet (`com.michaeltchuang.seedvault.demo`), UI only, built on `seed-vault-sdk`. Algorand tab talks to `seed-vault-service` over AIDL, Solana tab to the Solana Mobile Seed Vault. |

None of them may depend on `wallet-sdk-ui`. Only `seed-vault-service` uses `wallet-sdk-core`, as an embedded `.aar` (see Local `.aar` libraries). `seed-vault-sdk` and `seed-vault-demo-app` do not depend on `wallet-sdk-core`.

## SDK (`seed-vault-sdk`)

- No UI and no Compose. Network calls (algod, Solana RPC) and Seed Vault access live here so apps other than the demo can reuse them.
- Public API changes are releases: it is published on every `vX.Y.Z` tag by `publish-sdk-maven.yml`, with the version taken from the tag. Keep changes backwards compatible, or bump the version deliberately.
- Keep the AIDL copy and `SeedVaultServiceConstants` in sync with `seed-vault-service` (see AIDL service).
- Dependencies on the Solana Mobile SDK, sol4k and Ktor are `implementation`, so they appear as runtime dependencies in the published POM. Do not leak their types in public signatures unless the dependency is moved to `api`.

## Where secrets live: `SeedKeyVault`

Secret keys sit behind `SeedKeyVault` (interface in `domain/vault`, implementations in `data/vault`), with two implementations. The repository picks one per seed and each seed row remembers its choice in the `storage` column.

- `RoomSeedKeyVault`: entropy and private key encrypted by `KeystoreSecretCipher` (AES-256-GCM, StrongBox when present else TEE, usable only within 60 s of a strong biometric / screen lock verification), stored in the seed row. Add, reveal and sign therefore need the user to verify first; the UI wraps them in `rememberRequireAuth`. The index 1 (autosign) private key is a second copy under a StrongBox key that needs no user (`AUTOSIGN_KEY_ALIAS`), so Room seeds can autosign. Both addresses share one Falcon key, so the fingerprint gate on index 0 is software-only against code inside the app. Used when the device has no Trusty applet. This is also the emulated path.
- `TrustySeedKeyVault`: keys stay in the Trusty applet in the TEE, reached through `libs/algokit_seed_vault.aar`. The seed row keeps only the address and public key, the encrypted columns are empty. It is available only when `client.status()` is `Available`; any failure counts as unavailable.

Rules:

- New code that touches a secret goes through `SeedKeyVault`, so both storages keep working. Add the method to the interface, implement it in both vaults, and fake it in tests.
- New seeds go to Trusty when it is available, otherwise Room. Existing seeds keep their storage. Using a seed whose storage is unavailable must fail with a clear error.
- The applet signs through `signTransaction` and `signArbitraryData` with an address index. Every seed has two addresses, one key: index 0 (`HUMAN_SIGN_ADDRESS_INDEX`, applet `humanSignIndex()`) and index 1 (`AUTOSIGN_ADDRESS_INDEX`, applet `autosignIndex()`). Trusty imports the seed once per index; Room derives both addresses from the one key. Accounts live in the `seed_account` table with their own key pair (`public_key`, `encrypted_private_key`), address and salt; `seed` keeps only the encrypted entropy. Both accounts currently hold the same key, but code must read the key from the account being signed with, never assume it from the seed.
- Wrap anything that loads the native library so it cannot crash on a device without it, and keep it lazy (no native calls at class load) so JVM unit tests work. Fake `SeedVaultClientInterface` in tests.
- Room is the metadata store for every seed: name, backup state, address, public key, authorized apps. `address` is unique, so importing a phrase that is already stored returns the existing seed.

## AIDL service

- The contract is `ISeedVaultService.aidl` in `seed-vault-service/src/main/aidl/...`. **`seed-vault-sdk` has a copy; edit both together.** Keep `SeedVaultContract` (service) and `SeedVaultServiceConstants` (client) in sync with the manifests.
- Signing calls identify the account by its **address** (`signBytes(address, ...)`, `signTransaction(address, transaction)`), never by seed id. A seed may have several derived addresses later, so the seed id is an internal detail: the service resolves the address to one of the caller's authorized seeds. Confirm-sign extras follow the same names (`EXTRA_ADDRESS`, `EXTRA_DATA`, `EXTRA_TRANSACTION`).
- The service is exported and protected by the dangerous permission `<package>.ACCESS_SEED_VAULT`. Every call also checks the caller's uid, resolved with `Binder.getCallingUid()`, against the seed's `authorized_app` rows. Throw `SecurityException` for an unauthorized caller. Never trust a package name the caller sends.
- Every AIDL signature needs a matching one-time approval (`SignApprovalCoordinator`, 60 s, per app and exact operation) from the confirm sign screen, where the user verifies with fingerprint / screen lock (`UserAuthenticators`: Trusty applet if it can, else the system prompt). No way to verify means no. Clients call `CONFIRM_SIGN` before every sign call. The one exception is the autosign address (index 1) while `AutosignSettings` is on: it signs without approval. The setting is **on by default** and the user can turn it off on the Security screen; the service reads it on every call. The index 0 address always needs approval.
- AIDL returns only public data (id, name, address, public key) and signatures. Never return a seed phrase, entropy, seed or private key.
- `signTransaction` is generic: the client builds the unsigned transaction (the SDK has `AlgorandTransactions`) and the vault signs it. So the vault must show the user what it signs: it decodes the transaction (`TransactionDecoder`), refuses bytes that are not a transaction or whose sender is not the signing address, and the confirm sign screen lists the type, receiver, amount, fee and a warning for a close-out or rekey. The approval is bound to the exact transaction bytes. Do not add a signing path that skips the decode and the confirm screen.
- `MainActivity` is the only activity: a Compose `NavHost` that also hosts the authorize screen (`AUTHORIZE_APP` action) and the confirm sign screen (`CONFIRM_SIGN`, started by the client with `startActivityForResult`; a service cannot open a screen from the background). Do not add activities.
- Authorization is started by the client with `startActivityForResult`. The caller package comes from `callingActivity`, never from an extra.
- The vault never talks to the network. The client fetches algod params and sends the signed transaction.
- Anything that can reach a seed phrase or key shows `SecureScreen()` (FLAG_SECURE).

## Demo client (`seed-vault-demo-app`)

- UI only: business logic belongs in `seed-vault-sdk`.
- Two tabs: Algorand (Seed Vault Service over AIDL, TestNet via algod) and Solana (Solana Mobile Seed Vault through its SDK, DevNet). Solana does not depend on the AIDL connection: the Falcon-1024 keys cannot sign Solana transactions.
- The Solana Mobile Seed Vault is not a bound service. It is a content provider plus activities, reached through the `Wallet` SDK.
- Algorand network calls live in `AlgorandRepository`, Solana ones in `SolanaBalanceRepository` / `SolanaTransferRepository`, all in `seed-vault-sdk`.
- Explorer links: Pera TestNet explorer for Algorand, Solana explorer `?cluster=devnet` for Solana.

## Local `.aar` libraries

- Put an `.aar` in `<module>/libs/` and reference it explicitly: `implementation(files("libs/algokit_seed_vault.aar"))`. Replace it with the Maven coordinate when it is published.
- A local `.aar` brings no transitive dependencies, so declare them by hand (the seed vault client needs `net.java.dev.jna:jna@aar`).
- `wallet_sdk_core.aar` is `wallet-sdk-core` built from `algokit-walletsdk-kmp`, with its runtime dependencies declared by hand in `seed-vault-service/build.gradle.kts` (see the README for how to refresh it). It must be built without the `META-INF/*.kotlin_module` packaging exclude, otherwise Kotlin cannot see its top-level functions. Move to the Maven coordinate once a release includes that fix.
- Do not add local copies of `algokit_composer`, `algokit_crypto` or `algokit_transact`: they come in through the `wallet-sdk-core` dependencies, and two builds of the same native library conflict at runtime.

## Checks

```
./gradlew ktlintFormat
./gradlew :seed-vault-service:testDebugUnitTest :seed-vault-service:ktlintCheck :seed-vault-service:assembleDebug :seed-vault-sdk:assembleDebug :seed-vault-demo-app:assembleDebug
```

After a package rename or a Room schema change, uninstall the app on the device before installing, and install `seed-vault-service` before `seed-vault-demo-app` so the permission exists when the client is installed.
