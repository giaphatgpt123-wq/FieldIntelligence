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

## Work continuation — 2026-09-25
- HEAD before work: e7d80b346ffc12e12375c2675f8436dd5032308c; Android Build #141 completed success. Builds #139 and #140 also completed success.
- Code HEAD after work: d8fb1369b49a8487068c8957d2c474eb21c2c545. Android Build #144: in progress at last check; do not claim PASS yet. https://github.com/giaphatgpt123-wq/FieldIntelligence/actions/runs/36095311907
- Files changed: DataUpdateManager.kt, OfflineMapPack.kt, MainActivity.kt.
- Update flow now retains one fetched manifest/version; failed activation attempts to reinstall the prior active map package, or bundled map when no active package existed. Rollback installs the previous map before switching package metadata, with recovery attempt on error.
- CI is a build check, not an on-device map-render verification. Private release URL, hardcoded package filename and test-vector basemap remain unresolved.
- NEXT ACTION: check Build #144 conclusion and logs; fix failures. Then harden map/package transaction against process interruption and validate geometry records and duplicate ZIP entries before swap. Recheck CI and device map rendering.

## Work audit and upgrade — 2026-09-25
- Pre-handoff code HEAD a31af6a64b0f3494c2532e77a19a32e233b1df48. Android P0 Build #149 completed success: https://github.com/giaphatgpt123-wq/FieldIntelligence/actions/runs/36095840971. Earlier #146–#148 completed success.
- Changed OfflineMapPack.kt and OfflineMapPackTest.kt: strict ZIP entry names, duplicate rejection, line/polygon minimum points, finite/bounded coordinate validation before swap, reject orphan geometry. Installed map no longer receives bundled bootstrap sample files on restart.
- Changed EmergencyScreen.kt: scrollable Field screen exposes previously clipped map; data update copy now states scientific library/content updater is not yet implemented.
- CI verifies unit tests and debug APK build; on-device map visibility and update interruption recovery remain unverified.
- Known blockers: bundled Mekong vectors are test data only; private GitHub release cannot serve unauthenticated APK; package URL points to fixed offline-map-2.pack; app bottom navigation and several feature actions are placeholders; no production recognition model/library pipeline.
- NEXT ACTION: verify map visibility on a device with the latest APK; provide safe public data-only host or backend by explicit authorization, then replace fixed package URL with manifest-derived safe asset location. Make map/package transaction crash recoverable, add a device/instrumentation test for install and rollback, and replace placeholder navigation/actions incrementally.

## Continuation — navigation and private update channel
- Code HEAD 271f9f73ceac809185878fd13111e0d9e9d15095 (prior to this handoff edit). Android P0 Build #151 for navigation passed; CI for later commits pending at this checkpoint.
- EmergencyScreen.kt: bottom tabs now open Home, Field, Recognition, Library and Settings; update controls moved from Training to Settings. Buttons without implemented actions show unavailable rather than silently doing nothing.
- UpdateConfig.kt and MainActivity.kt: private repository release endpoint is disabled for unauthenticated APK, with a direct status explanation. Existing HTTPS URLs remain placeholders until a safe data-only endpoint is supplied. No repository access token is embedded.
- NEXT ACTION: check latest CI on this handoff HEAD; fix failures. Establish an approved public data-only distribution endpoint or authenticated backend, then use stable package naming/manifest URL, validate redirects and resume update integration. Device check still needed for map visibility and navigation.

## Continuation — transaction recovery and stable package name
- Code HEAD before this handoff: 2916bf911107513d844dc825d4a44a5a71089352; CI for this batch was running at the last check. Do not claim PASS until the HEAD run concludes.
- DataUpdateManager.kt now prepares replacement pack before replacing current.pack, retains previous copy, and writes a pending map-swap marker. MainActivity.kt recovers an interrupted swap at startup by reinstalling the active pack, or bundled data if no active pack exists. It also marks update and rollback map swaps in progress and clears the marker after success or successful restore.
- Publish workflow now creates stable offline-map.pack while version stays in manifest. Client placeholder URL and docs match the filename. The private release channel remains disabled in the app; no public publication or token embedding.
- Limit: crash recovery is code-reviewed and build-tested only, without a process-kill device test. The map install/metadata sequence still needs an instrumentation test under interruption. Operations currently run on the UI thread during map installation and should be moved to IO with UI state updates on main.
- NEXT ACTION: check CI for the latest HEAD, inspect logs if failed. Add meaningful interruption/rollback tests; move heavy map installation off UI thread. For usable remote updates, arrange an explicitly authorized public data-only endpoint or safe backend and verify redirects and manifest/package integrity. Verify latest map on a device.

## Latest verified checkpoint — 2026-09-25
- Code commit 497f23620e382b96083e5e15aac42393a6ab904b: Android P0 Build #163 completed success, https://github.com/giaphatgpt123-wq/FieldIntelligence/actions/runs/36107094806. Previous #156–#162 also completed success.
- Map update and rollback now run file/network work on Dispatchers.IO; Compose state reload remains on main. Concurrent update/rollback actions are guarded by updateBusy.
- Crash marker plus prepared replacement package aim to reconcile an interrupted map swap on next launch. CI proves compilation/unit tests only; process-kill device test has not happened.
- Distribution remains BLOCKED: private release is inaccessible to unauthenticated APK. The stable filename is prepared in publisher/client config, but the app deliberately does not enable the private endpoint. No public asset was published in this work.
- NEXT ACTION: test APK on device: map visibility, bottom navigation, track persistence and offline use. Add instrumentation tests for interrupted update and rollback. For live data updates, authorize a separate data-only public endpoint or implement a safe authenticated backend; then validate redirect handling and download integrity before enabling the button. Continue building real offline map and scientific recognition data; do not mistake vector bootstrap for real basemap.

## Continuation — map visibility and atomic swap
- Code HEAD before handoff edit: 5d52905dcb1ea5e36f87c3b371fe61b374f46618. At last check #165–#167 success, #168–#169 in progress. Check HEAD CI before claiming success.
- EmergencyScreen.kt now shows map immediately below position and explains when an installed package has no visible local map features. This does not add real map coverage.
- DataUpdateManager.kt uses Files.move with ATOMIC_MOVE for active/previous pack replacement. OfflineMapPack.kt requires atomic same-filesystem directory moves; an unsupported move fails rather than copying/deleting live map. At startup it tries to restore an interrupted backup before asset bootstrap and never discards an unrestored backup.
- CI does not emulate forced process death or device filesystem behavior. A device test must confirm atomic move support, map rendering and recovery. Private data channel remains disabled.
- NEXT ACTION: check latest CI and fix failures; add process-interruption and rollback instrumentation checks on a real Android device. Build a verified real offline basemap, then confirm geographical coverage and usability. Establish authorized data-only distribution before enabling remote update.

## Device screenshot checkpoint — 2026-09-25
- User screenshot of earlier APK confirms GPS position 10.38697, 105.62701 ±6 m; vector canvas displays 5 points and 3 lines from bundled test pack, 0 polygons and 0 breadcrumb points. Thus the canvas rendered on this device, but it is not a real basemap. The screenshot does not confirm functionality of the later APK.
- Code HEAD 16e9bef8664fac113f99f9ebb2bf4ad78d7ae186: Android P0 Build #171 completed success, https://github.com/giaphatgpt123-wq/FieldIntelligence/actions/runs/36115925609.
- EmergencyScreen.kt shows bytes for tiny packs, labels the canvas as vector schematic lacking road/terrain basemap, preserves geographic aspect ratio using latitude-adjusted longitude scale, and retains GPS-follow during pinch zoom.
- NEXT ACTION: test new APK on same device for map proportions, GPS-follow, pan/zoom and readable sample warning. The actual remaining map work is a licensed, accurate offline basemap/coverage pipeline; do not describe the five-point test vector as a usable field map. Device verification and private data hosting remain unresolved.
