#!/usr/bin/env bash
set -euo pipefail
MANIFEST="app/src/main/AndroidManifest.xml"
if grep -q 'android.permission.INTERNET' "$MANIFEST"; then echo 'FAIL: INTERNET permission found'; exit 1; fi
if grep -q 'android.permission.CALL_PHONE' "$MANIFEST"; then echo 'FAIL: CALL_PHONE permission found'; exit 1; fi
if grep -R -E 'FirebaseAnalytics|Crashlytics|Sentry|Telemetry|OkHttpClient|Retrofit.Builder' app/src/main/java --include='*.kt' | grep -v '^$'; then echo 'FAIL: network/telemetry SDK marker found'; exit 1; fi
echo 'PASS: privacy gate'
