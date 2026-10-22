#!/usr/bin/env bash
# Run a command with Infisical secrets injected as env vars (never written to disk).
#   scripts/with-secrets.sh /postgres -- pnpm --dir web start
# Uses the machine identity in the repo-root .env (INFISICAL_CLIENT_ID/SECRET/PROJECT_ID/ENVIRONMENT/DOMAIN).
set -euo pipefail
cd "$(dirname "$0")/.."
path="$1"; shift; [[ "${1:-}" == "--" ]] && shift
set -a; . ./.env; set +a
INFISICAL_TOKEN=$(infisical login --method=universal-auth --client-id="$INFISICAL_CLIENT_ID" \
  --client-secret="$INFISICAL_CLIENT_SECRET" --domain="$INFISICAL_DOMAIN" --plain --silent)
export INFISICAL_TOKEN
exec infisical run --projectId="$INFISICAL_PROJECT_ID" --env="$INFISICAL_ENVIRONMENT" \
  --domain="$INFISICAL_DOMAIN" --path="$path" --silent -- "$@"
