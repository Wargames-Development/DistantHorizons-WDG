#!/usr/bin/env bash
set -euo pipefail

repository="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output="${1:-$repository/build/source-packages/DistantHorizons-WDG-change-006-source.zip}"
top_level="DistantHorizons-WDG"

required=(
  gradlew
  gradlew.bat
  gradle/wrapper/gradle-wrapper.jar
  gradle/wrapper/gradle-wrapper.properties
  gradle/gradle-daemon-jvm.properties
  buildSrc/gradle/gradle-daemon-jvm.properties
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  dependencies.gradle
  repositories.gradle
  jitpack.yml
  src/main/resources/mcmod.info
  src/main/resources/META-INF/distanthorizons_at.cfg
  src/main/resources/mixins.distanthorizons.json
  src/main/resources/mixins.distanthorizons.early.json
  src/main/java/com/seibel/distanthorizons/coreapi/ModInfo.java
  buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/FoundationSupport.java
  buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/RepositoryContractVerifier.java
  buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/DistantHorizonsArtifactVerifier.java
  buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/Lwjgl3ifyCompatibilityVerifier.java
  buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/PublishedMetadataVerifier.java
  buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyRepositoryTask.java
  buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyProductionModArtifactTask.java
  buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyWdgLwjgl3ifyCompatibilityTask.java
  buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyPublishedDependencyMetadataTask.java
  buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/RuntimeArtifactVerifier.java
  buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/CombinedClientSupport.java
  buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyRuntimeArtifactTask.java
  buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyRequiredRuntimeArtifactsTask.java
  buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/PackageCombinedClientTask.java
  buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyCombinedClientPackageTask.java
  buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyCombinedClientReproducibilityTask.java
  buildSrc/src/test/java/com/wargamesdevelopment/distanthorizons/gradle/FoundationSupportTest.java
  buildSrc/src/test/java/com/wargamesdevelopment/distanthorizons/gradle/ArtifactVerifierTest.java
  buildSrc/src/test/java/com/wargamesdevelopment/distanthorizons/gradle/RuntimeArtifactVerifierTest.java
  buildSrc/src/test/java/com/wargamesdevelopment/distanthorizons/gradle/CombinedClientSupportTest.java
  README.md
  SETUP.md
  COMPILING.md
  docs/DEPENDENCIES.md
  docs/COMBINED_CLIENT.md
  scripts/build-gtnhlib-0.11.31.sh
  scripts/package-source.sh
)

for relative in "${required[@]}"; do
  test -f "$repository/$relative" || {
    printf 'ERROR: required source file is missing: %s\n' "$relative" >&2
    exit 1
  }
done

mkdir -p "$(dirname "$output")"
temporary="$(mktemp -d "${TMPDIR:-/tmp}/distant-horizons-source.XXXXXX")"
trap 'rm -rf "$temporary"' EXIT
mkdir -p "$temporary/$top_level"

list="$temporary/source-files.txt"
if test -d "$repository/.git" && git -C "$repository" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git -C "$repository" ls-files --cached --others --exclude-standard > "$list"
else
  (
    cd "$repository"
    find . -type f -print | sed 's#^./##' | LC_ALL=C sort
  ) > "$list"
fi

python3 - "$list" <<'PY'
import pathlib, sys
p = pathlib.Path(sys.argv[1])
for raw in p.read_text().splitlines():
    path = raw.replace('\\', '/')
    lower = path.lower()
    forbidden_roots = {
        '.git', '.gradle', 'build', 'run', 'eclipse', '.idea', '.vscode',
        'logs', 'crash-reports', 'config', 'saves', 'validation-logs',
        'source-packages', 'distributions', 'combined-client', 'staging',
        'native', 'natives', 'curseforge-profiles', 'external-build', 'gtnhlib-build',
        'runtime-packages'
    }
    parts = pathlib.PurePosixPath(path).parts
    if parts and parts[0].lower() in forbidden_roots:
        raise SystemExit(f'ERROR: forbidden source package root selected: {path}')
    if len(parts) >= 2 and parts[0].lower() == 'buildsrc' and parts[1].lower() in {'build', '.gradle'}:
        raise SystemExit(f'ERROR: forbidden buildSrc output selected: {path}')
    if any(part.lower() == '__macosx' for part in parts):
        raise SystemExit(f'ERROR: Finder archive metadata selected: {path}')
    if lower.endswith(('.ds_store', '.log', '.sqlite', '.sqlite3', '.db', '.db-wal', '.db-shm', '.db-journal', '.lod')):
        raise SystemExit(f'ERROR: forbidden runtime file selected: {path}')
    if lower.endswith(('.zip', '.tar', '.tar.gz', '.tgz')):
        raise SystemExit(f'ERROR: nested archive selected: {path}')
    if lower.endswith('.jar') and path != 'gradle/wrapper/gradle-wrapper.jar':
        raise SystemExit(f'ERROR: generated/local JAR selected: {path}')
PY

while IFS= read -r relative; do
  test -n "$relative" || continue
  source_path="$repository/$relative"
  test -f "$source_path" || {
    printf 'ERROR: selected source file disappeared: %s\n' "$relative" >&2
    exit 1
  }
  mkdir -p "$temporary/$top_level/$(dirname "$relative")"
  cp -p "$source_path" "$temporary/$top_level/$relative"
done < "$list"

# Stable timestamps plus sorted input make repeated packages byte-for-byte reproducible.
find "$temporary/$top_level" -exec touch -t 198001010000 {} +

rm -f "$output"
(
  cd "$temporary"
  find "$top_level" -type f -print | LC_ALL=C sort | zip -X -q "$output" -@
)

listing="$temporary/archive-listing.txt"
unzip -Z1 "$output" > "$listing"
test -s "$listing"
if grep -Ev "^${top_level}/" "$listing" >/dev/null; then
  printf 'ERROR: archive contains a path outside %s/\n' "$top_level" >&2
  exit 1
fi
if grep -Ei "^${top_level}/(\.git|\.gradle|build|run|eclipse|\.idea|\.vscode|logs|crash-reports|config|saves|curseforge-profiles|external-build|gtnhlib-build|runtime-packages|__MACOSX)(/|$)|/buildSrc/(build|\.gradle)(/|$)|\.DS_Store$|\.(sqlite3?|db|lod|log)$" "$listing" >/dev/null; then
  printf 'ERROR: archive listing contains forbidden generated/runtime data\n' >&2
  exit 1
fi
for relative in "${required[@]}"; do
  grep -Fxq "$top_level/$relative" "$listing" || {
    printf 'ERROR: packaged archive lacks required file: %s\n' "$relative" >&2
    exit 1
  }
done

printf 'Source package: %s\n' "$output"
printf 'SHA-256: '
if command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$output" | awk '{print $1}'
else
  sha256sum "$output" | awk '{print $1}'
fi
printf 'Files: %s\n' "$(wc -l < "$listing" | tr -d ' ')"
printf 'Top-level directory: %s/\n' "$top_level"
printf 'DistantHorizons-WDG source packaging PASSED\n'
