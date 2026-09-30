#!/usr/bin/env bash
set -euo pipefail
if grep -R -E 'URLSession|NWConnection|Firebase|Crashlytics|Sentry|Telemetry' HoiDungMoDung --include='*.swift'; then
  echo 'FAIL: network/telemetry marker found'; exit 1
fi
echo 'PASS: iOS privacy gate'
