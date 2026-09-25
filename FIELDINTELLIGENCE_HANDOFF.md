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

## OSM regional pilot preparation — 2026-09-25
- Code HEAD before this handoff: 9c5e57c126aa7312c000bf5264b9ccd269d2999a. Android CI #177 pending at this checkpoint. Local Python tests: 2 passed. Check newest CI before reporting PASS.
- Added tools/build_vector_pilot.py, tools/test_build_vector_pilot.py, docs/OSM_VECTOR_PILOT.md and a Python test step in android-build.yml. Builder accepts a small OSM XML extract and produces bounded .region/.lines/.points ZIP, SHA-256 manifest and provenance record. It has strict segment/size caps and labels output PILOT_ONLY_NOT_FOR_NAVIGATION. No real OSM data was downloaded, committed, published or installed.
- Source assessment: Geofabrik Vietnam current whole-country PBF is about 313 MB, above 64 MB app pack cap; OSM data requires attribution and ODbL compliance. Osmium documentation supports bbox extraction and PBF/XML conversion. Pilot is not a road/terrain basemap or route guidance.
- NEXT ACTION: check CI #177 and later handoff HEAD, resolve failures. When an approved regional source and suitable map engine are ready, generate one small private test pack, verify source/checksums/license, validate performance and rendering on a phone, then replace sample vectors. Design indexed regional offline tile renderer for production. Current remote update channel remains disabled because the repository is private.

## Regional OSM APK test checkpoint — 2026-09-25
- Main code HEAD before this handoff commit: f6bda3c6a05814cae3379fc37ad69ffde25daad2. Android P0 Build #190 completed success: https://github.com/giaphatgpt123-wq/FieldIntelligence/actions/runs/36120094988. Private data-publish run #3 completed success; app remote data channel remains disabled.
- PR #1 merged at f8173e831a123986e5daf67585e6a48daca72608 after PR CI #187 success. Bundled assets now contain osm-pilot-105-10 (.region, .lines, .points), replacing the 5-point Mekong test vectors. Private OSM build artifact verified: 1,328 road segments, 10,192 vertices, 63,829-byte ZIP, SHA-256 52d68964f8c22892556b30f6d73dfa5ad977e9f23c4f6958bc6d2c47f16be0f2. Provenance and country-source SHA are in docs/osm-provenance.
- Bounds 10.33–10.44 N, 105.57–105.68 E contain user screenshot GPS 10.38697,105.62701. This is a small road schematic without terrain, guidance, road names or nationwide coverage; OSM attribution appears for region IDs beginning osm-. No device rendering of the new data has been verified.
- Debug APK uses package vn.fieldintel.app.pilot and label VN Sinh tồn thử nghiệm so it can coexist with the existing app; old track data stays with the existing app. Artifact from #190 inspected: bundled OSM files present; APK SHA-256 980891ce9f50ba94103e2d441f9f3ed518a18bc5839608416d56048179c9effa. Library delivery: FieldIntelligence-OSM-pilot-debug.apk.
- Intermediate Android CI #182 failed while app/UI attribution parameters were temporarily inconsistent; #183 and later runs passed. OSM pilot workflow duplicate run #2 failed in a branch-push race; run #3 succeeded and PR #1 merged.
- NEXT ACTION: install the pilot APK beside the current app and confirm roads render around the screenshot GPS, panning/zooming is responsive, credit is visible, track data remains in the existing app, and no crash occurs. If results are good, expand coverage using indexed per-region offline tiles rather than scaling the current Canvas to the whole country. Remote data updates require a separate authorized endpoint and stable production signing; do not describe this pilot as a complete survival app or navigation map.

## Pilot installation and coverage checkpoint — 2026-09-25
- User confirmed `vn.fieldintel.app.osmtest` installed alongside their existing app without package conflict. Device map rendering and performance of this new package are not yet confirmed.
- Test-only debug signing and separate `.osmtest` package implemented; never use the test key for `vn.fieldintel.app` production. Android P0 Build #194 completed success on `3c3fe421412c9bb2245b6916122de888004c2fb7`: https://github.com/giaphatgpt123-wq/FieldIntelligence/actions/runs/36123360836. APK manifest and OSM assets inspected.
- Added explicit GPS/map coverage diagnostics in EmergencyScreen.kt and MainActivity.kt: shows the loaded region bounds when GPS is inside, and the available bounds when outside. Android P0 Build #197 completed success on `d40bc742b320c403144412531f9106e12018f133`: https://github.com/giaphatgpt123-wq/FieldIntelligence/actions/runs/36131417023. CI verifies build/tests only.
- NEXT ACTION: deliver #197 APK and ask user to confirm roads, map attribution and pan/zoom at GPS 10.38697,105.62701 (inside 10.33–10.44N, 105.57–105.68E). Device outside region must show explicit outside-coverage status. If successful, profile/implement indexed regional map loading and expand verified offline coverage. Data update endpoint remains blocked by private repository and requires authorized data-only distribution; scientific recognition remains incomplete.

## User screenshot: map blank while GNSS pending — 2026-09-25
- User screenshot from coverage build shows "Đang chờ tín hiệu vệ tinh", 3 offline-map files / 209 KB, and canvas 0 points / 0 roads. Root cause in MainActivity: geometry was loaded only inside GNSS callback, and a null/out-of-coverage GNSS fix cleared geometry. Canvas also included a faraway GPS coordinate when computing map fit, shrinking the pilot roads.
- Fixed MainActivity to load the first bundled region during startup, retain an overview when GPS is absent/outside loaded regions, and switch to matching region when available; fixed canvas map-fit to prioritize loaded geometry and avoid drawing out-of-region GPS marker. Files: MainActivity.kt, EmergencyScreen.kt. Code HEAD 19d40a35ef386bc2f3502224c14a5b3461a1e35c; Android P0 Build #200 completed success: https://github.com/giaphatgpt123-wq/FieldIntelligence/actions/runs/36132162125. Artifact APK inspected: `vn.fieldintel.app.osmtest`, 1,328 road lines and OSM assets, SHA-256 1491d6d7151a60db2214b5141b4d21eec8f26ea9c7ef6d56961ca4bb01dc4f2e.
- NEXT ACTION: install #200 artifact as update over `.osmtest` and verify roads are visible before GPS obtains a fix, OSM credit visible, and pan/zoom/position behavior. No device verification yet. Beyond pilot, implement indexed offline map renderer and expand sourced coverage; remote update still blocked by private repository.

## Pilot preview versus live GPS — 2026-09-25
- User screenshot showed 1,328 pilot OSM road lines rendered, but GPS was 10.60325,104.42022, outside bundled pilot coverage 10.33–10.44N / 105.57–105.68E. Screenshot still labeled the map `Theo GPS`, which was misleading.
- EmergencyScreen.kt now puts an explicit outside-coverage preview warning before the map, labels preview instead of GPS-follow, and disables the current-position button when outside. VersionCode bumped to 4 / 0.2.2-osm-pilot for the same `.osmtest` package and pilot signing key.
- Code HEAD cb5b3cb0e157d329c88083e935c9108277890013; Android P0 Build #203 completed success: https://github.com/giaphatgpt123-wq/FieldIntelligence/actions/runs/36133015147. Artifact verified package and 1,328 road segments, SHA-256 2c067e918543b047496bb7817665fb17e49293d89f1b0983da06a18b42929a78. On-device result of this change unverified.
- NEXT ACTION: verify new warning on device; build actual indexed offline coverage for the user's GPS area rather than treating the distant pilot preview as local map. Current app is a small vector schematic, not a usable terrain/navigation map. Remote update remains blocked by private repo distribution.

## Recognition and library pivot — 2026-09-25
- User requested a temporary focus shift from offline map to recognition/library. Added `SpeciesCatalog.kt` (2 offline botanical name records), `RecognitionLibraryPanel.kt` (search/filter/detail and image intake), `SpeciesCatalogTest.kt`, and `docs/RECOGNITION_LIBRARY_PILOT.md`. Wired gallery/camera callbacks and bounded in-memory image preview in MainActivity and SectionScreen. App versionCode 5; same `.osmtest` pilot signing.
- Records cite Royal Botanic Gardens, Kew POWO for Mangifera indica L. and Musa acuminata Colla. The taxonomy source establishes names only. Camera/gallery does not classify: result explicitly CHƯA XÁC ĐỊNH; no edibility/toxicity/medical claims. Other groups return empty rather than invented records. Images are shown in session memory, not uploaded by this feature.
- Android P0 Build #215 completed success for code+docs commit a9e2ace3c222445b3332bc49bc0dbc359a832e73: https://github.com/giaphatgpt123-wq/FieldIntelligence/actions/runs/36134572408. APK package checked `vn.fieldintel.app.osmtest`, SHA-256 1b0be160ae493ea9a12dfa84ad8120fbeb541bfcf1c4261a11b29f5b0973cb84. Device camera/gallery/search UI remains unverified.
- NEXT ACTION: test in-place APK update, camera and gallery preview, accent-insensitive query `xoai`, scientific query `MUSA ACUMINATA`, Nấm empty state, source-link behavior. Then design sourced species-data ingestion, multi-image evidence, multi-object recognition model and safety evaluation; never claim identification solely from the taxonomy catalog. Map remains a limited road schematic; remote scientific updates have no safe distribution channel yet.

## Device camera checkpoint and offline observations — 2026-09-25
- User screenshot shows `Nhận dạng` camera capture and thumbnail preview work on device; result remained CHƯA XÁC ĐỊNH. Gallery picker and library search still unverified on device.
- Implemented local observation journal: optional note (max 500 chars) plus captured/selected bounded preview JPEG stored under app-private files, metadata indexed in `index.json`. Library lists recent 20 and opens stored image/notes; old files are retained. No upload and no species identification. APK versionCode 6; same `.osmtest` signing identity. Files: app/ObservationStore.kt, MainActivity.kt, feature/emergency/RecognitionLibraryPanel.kt and EmergencyScreen.kt, docs/RECOGNITION_LIBRARY_PILOT.md.
- Android P0 Build #222 completed success at code SHA 534af097523cc6c0404a4669192a0a0d82e6748f: https://github.com/giaphatgpt123-wq/FieldIntelligence/actions/runs/36154953984. Artifact SHA-256 1613790d756129db81e0b01c53835f05d1134b0c68403897c607ad72ad13ee94, package `.osmtest` verified. Journal persistence has NOT been verified on-device; CI builds/tests do not cover restart, storage failure or camera full-resolution image.
- NEXT ACTION: install #222 artifact and verify save note/thumbnail, close/reopen app, open Library journal entry. Then improve multiple-view evidence, controlled scientific ingestion and evaluated multi-object recognition; never infer species or edibility from current photo preview.

## Observation library screenshot and controls — 2026-09-25
- User screenshot shows three saved offline journal entries labeled `lá` in Library and two taxonomy search results below. This confirms journal list presentation on device; it does not prove entries survive a force-close/restart.
- ObservationStore.kt now deduplicates only when the newest note and JPEG SHA-256 are identical within 120 seconds. Different images sharing a note remain separate. Journal detail allows confirmed deletion of one local image/metadata entry. VersionCode 7; same `.osmtest` package and pilot signature.
- Android P0 Build #231 completed success at code SHA 66b87d3aa553bbd76431314cdc22fc4614d1fe39: https://github.com/giaphatgpt123-wq/FieldIntelligence/actions/runs/36156717513. APK SHA-256 471d95cbfb8f08c907381ab197b2a0eded25c02e01c4bfe38e86e15a1fc89f90. CI does not exercise storage delete/dedup on a device.
- NEXT ACTION: verify update preserves three entries, deliberate rapid double save with same image/note creates one entry, confirmed deletion removes only the selected entry, and app restart retains remaining entries. Then build sourced species evidence/photo pipeline and evaluated multi-object identification; taxonomy catalog still has only two plant names.
