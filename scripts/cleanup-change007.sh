#!/usr/bin/env bash
set -euo pipefail

repository="${1:-$(pwd)}"
cd "$repository"

echo "Size before cleanup:"
du -sh .
rm -rf \
  build \
  buildSrc/build \
  .gradle \
  buildSrc/.gradle \
  external-build \
  gtnhlib-build \
  staging \
  combined-client \
  release-candidates \
  curseforge-profiles \
  runtime-packages \
  validation-logs/change007-static

echo
echo "Size after cleanup:"
du -sh .
echo
git status --short
git log -1 --oneline
