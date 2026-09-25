# Data Update Channel

FieldIntelligence separates application releases from offline data releases.

## Publish
Run the **Publish Data Update** workflow manually and provide an integer version.
The workflow creates:
- `manifest.json`
- `offline-map.pack` (stable asset name; version remains in the manifest)
- SHA-256 and exact byte size embedded in the manifest.

## Client safety contract
The Android client must:
1. use HTTPS only;
2. download into staging;
3. verify package name, minimum app version, size and SHA-256;
4. validate map region metadata;
5. atomically replace the active map;
6. retain rollback capability;
7. reload map state after activation.

Do not point production clients at mutable/unverified files. A data package failure must never delete a valid active map.

## Current distribution limitation
The repository is private. Android clients without GitHub authentication cannot use these release assets, so the built-in update button is intentionally unavailable. Do not embed a GitHub token or expose the repository. A separate authorized data-only host or authenticated backend is required before enabling remote updates.
