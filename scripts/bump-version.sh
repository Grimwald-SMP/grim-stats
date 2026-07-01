#!/usr/bin/env bash
# Bumps the mod version everywhere it lives (macOS / Linux).
#
# Updates:
#   - gradle.properties        mod_version
#   - dashboard/package.json   version (kept in lockstep with the mod)
#   - CHANGELOG.md             turns the Unreleased section into the new release
#
# Usage:
#   ./scripts/bump-version.sh patch          0.4.0 -> 0.4.1
#   ./scripts/bump-version.sh minor          0.4.0 -> 0.5.0
#   ./scripts/bump-version.sh major          0.4.0 -> 1.0.0
#   ./scripts/bump-version.sh 1.2.3          explicit version
#   ./scripts/bump-version.sh patch --tag    also creates an annotated git tag v<version>
#
# Tagging and pushing the tag is what triggers the release workflow on GitHub.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
props="$root/gradle.properties"
pkg="$root/dashboard/package.json"
changelog="$root/CHANGELOG.md"

arg="${1:?usage: bump-version.sh <major|minor|patch|x.y.z> [--tag]}"
tag="${2:-}"

current="$(sed -n 's/^mod_version=\([0-9][0-9.]*\)$/\1/p' "$props")"
[ -n "$current" ] || { echo "Could not find mod_version in gradle.properties" >&2; exit 1; }
IFS=. read -r major minor patch <<< "$current"

case "$arg" in
  major) next="$((major + 1)).0.0" ;;
  minor) next="$major.$((minor + 1)).0" ;;
  patch) next="$major.$minor.$((patch + 1))" ;;
  *[0-9].[0-9]*.[0-9]*) next="$arg" ;;
  *) echo "Version must be major, minor, patch, or x.y.z (got '$arg')" >&2; exit 1 ;;
esac
[ "$next" != "$current" ] || { echo "Version is already $current" >&2; exit 1; }

echo "Bumping $current -> $next"

# gradle.properties
sed -i.bak "s/^mod_version=.*/mod_version=$next/" "$props" && rm -f "$props.bak"

# dashboard/package.json
if [ -f "$pkg" ]; then
  sed -i.bak "s/\"version\": *\"[^\"]*\"/\"version\": \"$next\"/" "$pkg" && rm -f "$pkg.bak"
fi

# CHANGELOG.md: turn Unreleased into the new release section
if [ -f "$changelog" ] && grep -q '^## Unreleased' "$changelog"; then
  date="$(date +%Y-%m-%d)"
  sed -i.bak "s/^## Unreleased$/## Unreleased\n\n## $next - $date/" "$changelog" && rm -f "$changelog.bak"
fi

echo "Updated gradle.properties, dashboard/package.json, CHANGELOG.md"

if [ "$tag" = "--tag" ]; then
  [ -d "$root/.git" ] || { echo "--tag requested but this is not a git repository" >&2; exit 1; }
  git -C "$root" add gradle.properties dashboard/package.json CHANGELOG.md
  git -C "$root" commit -m "Release v$next"
  git -C "$root" tag -a "v$next" -m "GrimStats v$next"
  echo "Committed and tagged v$next. Push with: git push && git push origin v$next"
else
  echo "Next: review, commit, then tag with 'git tag -a v$next' to trigger a release."
fi
