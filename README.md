# Field Intelligence — P0 Emergency Core
New Android project scaffold based on the locked V13.0 architecture.

Implemented scaffold:
- :domain:emergency — protocol graph, validator, deterministic runner
- :data:emergency — initial Room emergency.db entities/DAO
- :feature:emergency — minimal one-hand emergency UI
- :app — launcher using the selected official survival logo
- unit-test fixtures for valid and revoked protocol graphs

Not yet implemented/verified:
- complete Room schema and migrations
- journal/checkpoint/recovery
- GNSS health + last reliable position
- Active/LKG/Golden signed protocol packs
- communication/handover
- authoritative medical protocol content
- fault injection/device tests
- APK build (Gradle/Android SDK build environment not available in this runtime)
