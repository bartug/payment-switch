#!/usr/bin/env bash
# POS terminali gibi imzalı istek atar.
#
# Kullanım:
#   scripts/pos-request.sh POST /v1/payments '{"amount":10.00,...}'
#   scripts/pos-request.sh GET  /v1/payments/<paymentId>
#
# Gerekli ortam değişkenleri: TERMINAL_ID, TERMINAL_SECRET
# İsteğe bağlı: BASE_URL (varsayılan http://localhost:8081), IDEMPOTENCY_KEY (POST'ta yoksa üretilir)

set -euo pipefail

METHOD=${1:?method gerekli (GET / POST)}
REQUEST_PATH=${2:?path gerekli (örn. /v1/payments)}
BODY=${3:-}
BASE_URL=${BASE_URL:-http://localhost:8081}
: "${TERMINAL_ID:?TERMINAL_ID tanımlı değil}"
: "${TERMINAL_SECRET:?TERMINAL_SECRET tanımlı değil}"

if [[ "$METHOD" == "POST" && -z "${IDEMPOTENCY_KEY:-}" ]]; then
  IDEMPOTENCY_KEY=$(uuidgen | tr '[:upper:]' '[:lower:]')
fi
IDEMPOTENCY_KEY=${IDEMPOTENCY_KEY:-}

TIMESTAMP=$(date +%s)
BODY_HASH=$(printf '%s' "$BODY" | openssl dgst -sha256 -hex | awk '{print $NF}')
STRING_TO_SIGN=$(printf '%s\n%s\n%s\n%s\n%s' "$METHOD" "$REQUEST_PATH" "$TIMESTAMP" "$IDEMPOTENCY_KEY" "$BODY_HASH")
SIGNATURE=$(printf '%s' "$STRING_TO_SIGN" | openssl dgst -sha256 -hmac "$TERMINAL_SECRET" -binary | base64)

ARGS=(-s -X "$METHOD" "$BASE_URL$REQUEST_PATH"
  -H "X-Terminal-Id: $TERMINAL_ID"
  -H "X-Timestamp: $TIMESTAMP"
  -H "X-Signature: $SIGNATURE")
if [[ -n "$IDEMPOTENCY_KEY" ]]; then
  ARGS+=(-H "Idempotency-Key: $IDEMPOTENCY_KEY")
  echo "Idempotency-Key: $IDEMPOTENCY_KEY" >&2
fi
if [[ -n "$BODY" ]]; then
  ARGS+=(-H "Content-Type: application/json" --data-binary "$BODY")
fi

curl "${ARGS[@]}"
echo
