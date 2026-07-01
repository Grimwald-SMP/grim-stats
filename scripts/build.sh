#!/usr/bin/env bash
# Full release build (macOS / Linux): dashboard + mod + tests, then prints the jar path.
#
# Usage:  ./scripts/build.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"

"$root/gradlew" build --console=plain

version="$(sed -n 's/^mod_version=//p' "$root/gradle.properties")"
echo
echo "Build complete:"
echo "  $root/mod/build/libs/grimstats-$version.jar"
echo "Drop it into a Fabric server's mods/ folder (alongside Fabric API)."
