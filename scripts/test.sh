#!/usr/bin/env bash
# Runs every test suite (macOS / Linux): mod JUnit tests + dashboard Vitest.
#
# Usage:  ./scripts/test.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"

echo "Mod tests (JUnit)..."
"$root/gradlew" :mod:test --console=plain

echo "Dashboard tests (Vitest)..."
(cd "$root/dashboard" && npx vitest run)

echo "All tests passed."
