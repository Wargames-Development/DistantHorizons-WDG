#!/usr/bin/env bash
set -u

repository="${1:-$(pwd)}"
log_root="${2:-$repository/validation-logs/change007-static}"
mkdir -p "$log_root"
cd "$repository" || exit 1

failures=0
index=0

run_check() {
  local label="$1"
  shift
  index=$((index + 1))
  local name
  name=$(printf '%02d-%s' "$index" "$(printf '%s' "$label" | tr ' /:' '---' | tr -cd 'A-Za-z0-9._-')")
  local log="$log_root/$name.log"
  printf '\n==> %s\n' "$label"
  "$@" >"$log" 2>&1
  local rc=$?
  if (( rc == 0 )); then
    printf 'PASS (%s) %s\n' "$rc" "$log"
  else
    printf 'FAIL (%s) %s\n' "$rc" "$log"
    tail -n 120 "$log"
    failures=$((failures + 1))
  fi
}

run_check "./gradlew --no-daemon --version" ./gradlew --no-daemon --version
run_check "./gradlew --no-daemon spotlessApply" ./gradlew --no-daemon spotlessApply
run_check "./gradlew --no-daemon spotlessCheck" ./gradlew --no-daemon spotlessCheck
run_check "./gradlew --no-daemon buildSrc:test" ./gradlew --no-daemon buildSrc:test
run_check "./gradlew --no-daemon checkstyleMain checkstyleTest" ./gradlew --no-daemon checkstyleMain checkstyleTest
run_check "./gradlew --no-daemon verifyRepository" ./gradlew --no-daemon verifyRepository
run_check "./gradlew --no-daemon test" ./gradlew --no-daemon test
run_check "./gradlew --no-daemon clean build" ./gradlew --no-daemon clean build
run_check "./gradlew --no-daemon verifyPublishedDependencyMetadata" ./gradlew --no-daemon verifyPublishedDependencyMetadata
run_check "./gradlew --no-daemon verifyProductionModArtifact" ./gradlew --no-daemon verifyProductionModArtifact

run_check "stale split-runtime reference scan" bash -c '
  set -euo pipefail
  files=(
    build.gradle.kts
    buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/PackageCombinedClientTask.java
    buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyCombinedClientPackageTask.java
    buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyCombinedClientReproducibilityTask.java
    buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyRequiredRuntimeArtifactsTask.java
    buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/ReleaseCandidateInputsTask.java
    buildSrc/src/main/java/com/wargamesdevelopment/distanthorizons/gradle/VerifyWdgReleaseCandidateTask.java
    README.md COMPILING.md SETUP.md
    docs/COMBINED_CLIENT.md docs/DEPENDENCIES.md docs/INSTALLATION.md
    docs/RELEASE_CANDIDATE.md docs/UPGRADE.md docs/ROLLBACK.md docs/WINDOWS_TESTING.md
  )
  if grep -InE "wdgLwjgl3ifyBundledClientPackage|wdgLwjgl3ifyRuntimeBundle|verifyNormalizedRuntimeBundle|lwjgl3ify-wdg-java21-runtimes\\.zip" "${files[@]}"; then
    echo "ERROR: Active Change 007 files still reference the obsolete split-runtime contract." >&2
    exit 1
  fi
'

external_count=0
for variable in WDG_LWJGL3IFY_JAR WDG_ANGELICA_JAR WDG_UNIMIXINS_JAR WDG_GTNHLIB_JAR; do
  if [[ -n "${!variable:-}" ]]; then
    external_count=$((external_count + 1))
  fi
done

if (( external_count != 0 && external_count != 4 )); then
  printf '\nFAIL: Set all four external artifact environment variables or none of them:\n' >&2
  printf '  WDG_LWJGL3IFY_JAR\n  WDG_ANGELICA_JAR\n  WDG_UNIMIXINS_JAR\n  WDG_GTNHLIB_JAR\n' >&2
  failures=$((failures + 1))
elif (( external_count == 4 )); then
  external_args=(
    "-PwdgLwjgl3ifyProductionJar=$WDG_LWJGL3IFY_JAR"
    "-PwdgAngelicaJar=$WDG_ANGELICA_JAR"
    "-PwdgUniMixinsJar=$WDG_UNIMIXINS_JAR"
    "-PwdgGtnhLibJar=$WDG_GTNHLIB_JAR"
  )

  run_check "verify runtime-bearing lwjgl3ify compatibility" \
    ./gradlew --no-daemon verifyWdgLwjgl3ifyCompatibility "${external_args[@]}"
  run_check "verify exact required runtime artifacts" \
    ./gradlew --no-daemon verifyRequiredRuntimeArtifacts "${external_args[@]}"
  run_check "package and verify Stage A" \
    ./gradlew --no-daemon packageBootstrapSmokeClient verifyBootstrapSmokeClient "${external_args[@]}"
  run_check "package and verify Stage B" \
    ./gradlew --no-daemon packageDistantHorizonsSmokeClient verifyDistantHorizonsSmokeClient "${external_args[@]}"
  run_check "package and verify Stage C" \
    ./gradlew --no-daemon packageCombinedClient verifyCombinedClientPackage "${external_args[@]}"
  run_check "verify Stage C reproducibility" \
    ./gradlew --no-daemon verifyCombinedClientReproducibility "${external_args[@]}"
  run_check "package and verify release candidate" \
    ./gradlew --no-daemon packageWdgReleaseCandidate verifyWdgReleaseCandidate "${external_args[@]}"
  run_check "verify release-candidate reproducibility" \
    ./gradlew --no-daemon verifyWdgReleaseCandidateReproducibility "${external_args[@]}"
  run_check "package CurseForge testing profile" \
    ./gradlew --no-daemon packageCurseForgeTestingProfile "${external_args[@]}"
  run_check "configuration-cache pass 1" \
    ./gradlew --no-daemon --configuration-cache verifyCombinedClientPackage verifyWdgReleaseCandidate \
      "${external_args[@]}"
  run_check "configuration-cache pass 2" \
    ./gradlew --no-daemon --configuration-cache verifyCombinedClientPackage verifyWdgReleaseCandidate \
      "${external_args[@]}"

  run_check "generated package member inspection" bash -c '
    set -euo pipefail
    package_count=0
    while IFS= read -r package; do
      test -n "$package" || continue
      package_count=$((package_count + 1))
      echo "--- $package"
      unzip -Z1 "$package"
      if unzip -Z1 "$package" | grep -Ei "(^|/)lwjgl3ify/runtime/|lwjgl3ify-wdg-java21-runtimes\\.zip|bundled-client"; then
        echo "ERROR: $package contains obsolete split-runtime content." >&2
        exit 1
      fi
    done < <(
      find build/combined-client/packages build/release-candidates build/curseforge-profiles \
        -type f -name "*.zip" -print 2>/dev/null | LC_ALL=C sort
    )
    test "$package_count" -gt 0
  '
else
  printf '\nExternal package tasks skipped: set the four WDG_*_JAR environment variables to enable them.\n'
fi

printf '\nGit status:\n'
git status --short --untracked-files=all || true
printf 'HEAD: '
git rev-parse HEAD || true
printf '\nChecks failed: %s\n' "$failures"
exit "$failures"
