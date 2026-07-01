#!/usr/bin/env bash
# GrimStats dev loop (macOS / Linux).
#
# Runs the Fabric test server (mod API on http://127.0.0.1:8765) and the Vite dev server
# (hot-reloading dashboard on http://127.0.0.1:5173) side by side. The dashboard talks to the
# mod API via VITE_API_BASE (see dashboard/.env.development); CORS is allowed by the mod's
# default config.
#
# Usage:  ./scripts/dev.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Starting GrimStats test server (rebuilds mod + dashboard)..."
"$ROOT/gradlew" :mod:runTestServer --console=plain &
SERVER_PID=$!

cleanup() {
  echo "Stopping test server..."
  kill "$SERVER_PID" 2>/dev/null || true
}
trap cleanup EXIT

echo "Starting dashboard dev server on http://127.0.0.1:5173 ..."
cd "$ROOT/dashboard"
npm run dev
