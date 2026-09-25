# FieldIntelligence / VN Sinh tồn — Chat ↔ Work Handoff

Updated: 2026-09-25

## Purpose
Canonical checkpoint for transferring the FieldIntelligence Android project between ChatGPT Chat and ChatGPT Work. Read this file first, then verify GitHub HEAD and current Actions before changing code. Never claim PASS without CI evidence.

## Repository
- Repository: giaphatgpt123-wq/FieldIntelligence
- Branch: main
- Android applicationId: vn.fieldintel.app
- App display name: VN Sinh tồn
- Official visual identity: compass + mountain + forest + river.

## Product scope
Offline-first field and marine survival assistant:
- Tropical plants, animals, insects, mushrooms recognition.
- Scientific evidence library with detailed species records and multi-angle references.
- Explicit UNKNOWN result when recognition evidence is insufficient.
- Safety warnings for harmful species and exposure/ingestion incidents.
- Survival procedures: water, shelter, fire, navigation, food preservation, camp craft and knots.
- First aid content with evidence/safety controls.
- Marine survival procedures.
- Offline positioning/map, breadcrumb tracking and TrackBack.
- Scientific/map/content data updates when online, independent from APK where possible.

## Confirmed device observations from user
User previously reported:
- App installs and opens.
- GPS works and position was correct.
- Track recording works.
- Track persists after reopening.
- Map did not display at that test point.
A later map-state propagation fix passed CI, but device rendering after that fix has not yet been re-verified by the user.

## Offline map architecture
Key files:
- app/src/main/java/vn/fieldintel/app/OfflineMapPack.kt
- feature/emergency/src/main/java/vn/fieldintel/feature/emergency/EmergencyScreen.kt
- app/src/main/assets/offline-map/

OfflineMapPack stores active map data under filesDir/offline-map and supports .region, .points, .lines, .polygons.
Current bundled Mekong South assets are TEST VECTOR DATA only, not a production basemap or complete road network.

Map renderer supports:
- points, lines, polygons
- breadcrumb and current GNSS position
- pan/zoom/follow behavior
- TrackBack state.

## Tracking / TrackBack
Track session persistence is implemented. User confirmed persistence on device. TrackBack shows remaining distance/bearing/off-track state. Existing warning correctly notes that following a historical breadcrumb does not guarantee present road/terrain safety.

## Data update subsystem
Key file: app/src/main/java/vn/fieldintel/app/DataUpdateManager.kt

Manifest fields:
- version
- schemaVersion
- minAppVersionCode
- packageName
- sha256
- sizeBytes

Implemented:
- HTTPS-only fetch/download.
- Package-name and minimum-app-version validation.
- SHA-256 and exact size verification.
- schemaVersion currently restricted to 1.
- manifest SHA format validation.
- download bounded by declared size and 64 MB hard limit.
- staging / active / previous package directories.
- rollback support.
- stagedPackage(version) exposes a verified staged package before activation.

Offline map package extraction hardening:
- path/name restriction and allowed extensions
- max 256 entries
- max 16 MB per extracted entry
- max 64 MB total extracted content
- validation before touching active map
- invalid pre-swap package cannot delete current active map
- swap failure restores previous map.

Current intended transaction:
download -> verify -> stage -> install/validate map -> activate update.
This replaces the unsafe earlier ordering that activated before map installation.

## Update UI
Update controls are currently exposed in the Training section rather than a dedicated Settings screen.
Controls:
- KIỂM TRA CẬP NHẬT
- KHÔI PHỤC GÓI TRƯỚC
- status text
Map pack file count/size is displayed.

## Data publishing workflow
Workflow:
.github/workflows/publish-data-update.yml

It can publish data-latest and automatically runs when bundled offline-map assets change.
A prior push-trigger version bug was fixed with fallback to github.run_number.

Known release-channel design issues:
1. Repository is PRIVATE. An unauthenticated Android app cannot reliably download private GitHub release assets.
2. Do NOT embed a GitHub PAT/token in the APK.
3. Current HttpURLConnection redirect policy is restrictive, while GitHub release downloads normally redirect.
4. Current app URL is tied to offline-map-2.pack. Future offline-map-N.pack releases will make that stale.
5. Prefer a public data-only endpoint/repository/CDN after explicit user approval, or a safe backend. Do not make this repository or user data public without explicit approval.
6. Better manifest design: include a safe package URL/name or publish a stable asset filename.

## CI checkpoint
At checkpoint creation:
- Build #136 PASS — installed app version retrieval without BuildConfig dependency.
- Build #137 PASS — hardened map extraction limits.
- Build #138 PASS — manifest validation and bounded downloads.
- Build #139 IN PROGRESS — expose verified staged package.
- Build #140 IN PROGRESS — install verified map before marking update active.
Do not infer results for #139/#140; query Actions again.

Earlier relevant PASS:
- #112 map-state propagation.
- #119 map package install.
- #120 runtime reload.
- #121 update pipeline.
- #123 safe configured update entry.
- #126 auto data publish.
- #128 data-release version fallback.
- #129 corrected data release.
- #130 app endpoint config.

## Recent commits
- 5e18f315f90d404e4b6fe22989d1ebcb0c42eb43 — installed app version without BuildConfig.
- 4b1e6ef208870df13225aff9e427e944e6dab838 — map extraction limits.
- 1c9c96615566615bfe9d3b7a8dbaa95e4ca516a6 — manifest/schema/download validation.
- 06c312646281fbb5301a3745bad7541659ba102c — expose staged package.
- a7967ae3b140f4cca29088e026a35adec29f7af0 — install/validate map before activation.

## Open technical risks / next actions
Priority order:
1. Check CI for current HEAD. Inspect exact logs and fix any failure before claiming PASS.
2. Audit transaction semantics after a7967ae: ensure manifest is not unnecessarily fetched twice and activation cannot leave inconsistent map/package state.
3. Audit rollback: rollback of update metadata and installed map must remain synchronized.
4. Deep-validate every .points/.lines/.polygons file before swap: finite/range-valid coordinates, minimum line/polygon point counts, malformed records rejected.
5. Reject duplicate ZIP entry names and consider rejecting all unexpected entries rather than silently skipping them.
6. Redesign release manifest/package URL so future releases are not hardcoded to offline-map-2.pack.
7. Resolve private-release hosting blocker without embedding credentials and without publishing anything without user approval.
8. Move update UI to dedicated Settings.
9. Add WorkManager policy for scheduled data updates, network constraints and retry/backoff only after endpoint architecture is safe.
10. Expand from test vector pack to a real offline map dataset/engine.
11. Scientific library updater is not yet wired to the data pipeline.
12. Recognition pipeline still requires production-grade model/data/evidence work and on-device validation.

## Safety / quality gates
- Never describe bootstrap vector data as a production-quality map.
- Never claim device behavior solely from CI.
- Never mark a build PASS until GitHub Actions reports success.
- Scientific identification must support uncertainty/UNKNOWN and evidence provenance.
- High-risk first aid/toxic species guidance requires conservative, scientifically sourced content.
- No public release of private repository/data without explicit user approval.
- No secret/token embedded in APK.

## Working protocol
When resuming in Chat or Work:
1. Read this handoff.
2. Fetch main HEAD and latest GitHub Actions.
3. Compare current code/CI to this checkpoint.
4. Continue from the highest-priority unresolved item.
5. Commit small auditable changes.
6. Verify CI.
7. Update this handoff after a meaningful milestone.

When returning from Work to Chat, update this file with:
- HEAD commit
- CI run/result
- files changed
- verified behavior
- unresolved failures/risks
- exact NEXT ACTION.
