#!/usr/bin/env bash
set -euo pipefail

repository="${1:?Usage: finalize-change007-release.sh /path/to/clean/checkout FULL_CHANGE007_COMMIT -PwdgLwjgl3ifyProductionJar=... -PwdgAngelicaJar=... -PwdgUniMixinsJar=... -PwdgGtnhLibJar=... [other current properties]}"
expected_commit="${2:?Full Change 007 commit is required}"
shift 2

required_properties=(
  wdgLwjgl3ifyProductionJar
  wdgAngelicaJar
  wdgUniMixinsJar
  wdgGtnhLibJar
)
for property in "${required_properties[@]}"; do
  found=false
  for argument in "$@"; do
    if [[ "$argument" == "-P${property}="* ]]; then
      found=true
      break
    fi
  done
  if [[ "$found" != true ]]; then
    echo "ERROR: Missing required finalization input -P${property}=/absolute/path/to/artifact" >&2
    exit 1
  fi
done

cd "$repository"
test -d .git
test -z "$(git status --porcelain)" || { echo "ERROR: finalization requires a clean checkout" >&2; exit 1; }
actual_commit="$(git rev-parse HEAD)"
test "$actual_commit" = "$expected_commit" || { echo "ERROR: HEAD $actual_commit != $expected_commit" >&2; exit 1; }

if grep -RInE \
  'wdgLwjgl3ify''(BundledClientPackage|RuntimeBundle)' \
  -- build.gradle.kts docs README.md COMPILING.md SETUP.md; then
  echo "ERROR: Active finalization inputs still reference the obsolete split-runtime contract." >&2
  exit 1
fi

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
  runtime-packages
./gradlew --no-daemon clean
./gradlew --no-daemon spotlessCheck buildSrc:test checkstyleMain checkstyleTest verifyRepository test build \
  -PwdgProvenanceMode=FINAL -PwdgExpectedCommit="$expected_commit" "$@"
./gradlew --no-daemon verifyPublishedDependencyMetadata verifyProductionModArtifact verifyRequiredRuntimeArtifacts \
  verifyBootstrapSmokeClient verifyDistantHorizonsSmokeClient verifyCombinedClientPackage \
  verifyCombinedClientReproducibility packageWdgReleaseCandidate verifyWdgReleaseCandidate \
  verifyWdgReleaseCandidateReproducibility packageCurseForgeTestingProfile \
  -PwdgProvenanceMode=FINAL -PwdgExpectedCommit="$expected_commit" "$@"

if find build/release-candidates build/combined-client/packages build/curseforge-profiles \
  -type f \( -path '*/lwjgl3ify/runtime/*' -o -name 'lwjgl3ify-wdg-java21-runtimes.zip' \) \
  -print -quit 2>/dev/null | grep -q .; then
  echo "ERROR: Final assets contain an obsolete external runtime bundle." >&2
  exit 1
fi

find build/release-candidates build/combined-client/packages build/curseforge-profiles -type f -print0 2>/dev/null \
  | sort -z | xargs -0 shasum -a 256
