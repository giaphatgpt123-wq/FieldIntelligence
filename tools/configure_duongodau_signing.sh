#!/usr/bin/env bash
set -euo pipefail

# One-time stable signing setup for the "Đường ở đâu" Android app.
# Prerequisites: gh CLI authenticated with permission to set Actions secrets.
# Usage:
#   ./tools/configure_duongodau_signing.sh /secure/path/duongodau-release.jks
# The script prompts for the keystore password without echoing it.

REPO="${DUONGODAU_GITHUB_REPO:-giaphatgpt123-wq/FieldIntelligence}"
KEYSTORE_PATH="${1:-}"

if [[ -z "$KEYSTORE_PATH" || ! -f "$KEYSTORE_PATH" ]]; then
  echo "Usage: $0 /secure/path/duongodau-release.jks" >&2
  exit 2
fi

command -v gh >/dev/null || { echo "Missing gh CLI" >&2; exit 3; }
command -v keytool >/dev/null || { echo "Missing keytool" >&2; exit 4; }

read -rsp "Keystore password: " STORE_PASSWORD
echo
KEY_PASSWORD="$STORE_PASSWORD"

keytool -list \
  -keystore "$KEYSTORE_PATH" \
  -alias duongodau \
  -storepass "$STORE_PASSWORD" >/dev/null

KEYSTORE_B64=$(base64 < "$KEYSTORE_PATH" | tr -d '\n')

printf '%s' "$KEYSTORE_B64" | gh secret set DUONGODAU_KEYSTORE_B64 --repo "$REPO"
printf '%s' "$STORE_PASSWORD" | gh secret set DUONGODAU_STORE_PASSWORD --repo "$REPO"
printf '%s' "$KEY_PASSWORD" | gh secret set DUONGODAU_KEY_PASSWORD --repo "$REPO"

unset KEYSTORE_B64 STORE_PASSWORD KEY_PASSWORD

echo "Stable signing secrets configured for $REPO."
echo "Next: run workflow 'Đường ở đâu Stable Release' with a monotonically increasing version_code."
