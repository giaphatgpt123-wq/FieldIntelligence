# Đường ở đâu — Stable signing setup

Stable package: `vn.duongodau.app`

Stable key alias: `duongodau`

Public certificate SHA-256 fingerprint:

`4fa4bc6a1b15e27ad7d6d158c5b13d35d082b2e57a8b473ba988935739edde0a`

## Required GitHub Actions secrets

Configure these repository secrets exactly once:

- `DUONGODAU_KEYSTORE_B64`
- `DUONGODAU_STORE_PASSWORD`
- `DUONGODAU_KEY_PASSWORD`

The actual values are intentionally **not stored in the repository**.

## Rules

1. Never commit the `.jks` file or secret text values.
2. Every stable release must use package `vn.duongodau.app`.
3. Every stable release must use alias `duongodau` and the same signing certificate fingerprint above.
4. `versionCode` must always increase.
5. Debug builds remain on the `.dev` package and must never replace the stable package.
6. The stable release workflow publishes both an immutable version archive and the fixed `duongodau-stable` update channel.
7. The in-app updater validates package name, APK SHA-256 and signing certificate before opening Android's installer.

## Recovery

The stable keystore must be backed up securely outside GitHub. Losing it prevents future in-place updates to already-installed stable builds.
