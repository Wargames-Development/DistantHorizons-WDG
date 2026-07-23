#!/usr/bin/env bash
set -euo pipefail

expected_source_sha256="2f5f46f6459cfccb384d892e354a2c27c2cf0d4f98da2818216521ca9d845d73"
expected_version="0.11.31"

if (( $# != 2 )); then
  printf 'Usage: %s /absolute/path/to/GTNHLib-0.11.31.zip /absolute/path/beneath/DistantHorizons-WDG/build\n' "$0" >&2
  exit 2
fi

source_zip="$1"
output_directory="$2"

test -f "$source_zip" && test -r "$source_zip" || {
  printf 'ERROR: GTNHLib source ZIP is not readable: %s\n' "$source_zip" >&2
  exit 1
}

case "$output_directory" in
  */build|*/build/*) ;;
  *)
    printf 'ERROR: output directory must be beneath a DistantHorizons-WDG build directory: %s\n' "$output_directory" >&2
    exit 1
    ;;
esac

hash_file() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    sha256sum "$1" | awk '{print $1}'
  fi
}

file_size() {
  if stat -f '%z' "$1" >/dev/null 2>&1; then
    stat -f '%z' "$1"
  else
    stat -c '%s' "$1"
  fi
}

source_before="$(hash_file "$source_zip")"
if [[ "$source_before" != "$expected_source_sha256" ]]; then
  printf 'ERROR: GTNHLib source SHA-256 mismatch.\nExpected: %s\nActual:   %s\n' \
    "$expected_source_sha256" "$source_before" >&2
  exit 1
fi

mkdir -p "$output_directory"
temporary="$(mktemp -d "${TMPDIR:-/tmp}/wdg-gtnhlib-0.11.31.XXXXXX")"
trap 'rm -rf "$temporary"' EXIT
extracted="$temporary/extracted"
mkdir -p "$extracted"

python3 - "$source_zip" "$extracted" <<'PY'
import pathlib
import stat
import sys
import zipfile

source = pathlib.Path(sys.argv[1])
destination = pathlib.Path(sys.argv[2])
with zipfile.ZipFile(source) as archive:
    names = archive.namelist()
    if len(names) != len(set(names)):
        raise SystemExit("ERROR: GTNHLib source ZIP contains duplicate members")
    folded = {}
    roots = set()
    for info in archive.infolist():
        raw = info.filename
        normalized = raw.replace("\\", "/")
        path = pathlib.PurePosixPath(normalized)
        if raw != normalized or normalized.startswith("/") or ".." in path.parts or "." in path.parts:
            raise SystemExit(f"ERROR: unsafe GTNHLib source archive path: {raw}")
        if len(path.parts) == 0:
            continue
        roots.add(path.parts[0])
        key = normalized.casefold()
        previous = folded.setdefault(key, normalized)
        if previous != normalized:
            raise SystemExit(f"ERROR: case-folding collision: {previous} vs {normalized}")
        mode = (info.external_attr >> 16) & 0o170000
        if mode == stat.S_IFLNK:
            raise SystemExit(f"ERROR: symlink member rejected: {raw}")
    if len(roots) != 1:
        raise SystemExit(f"ERROR: expected one GTNHLib source root, found: {sorted(roots)}")
    for info in archive.infolist():
        target = destination.joinpath(*pathlib.PurePosixPath(info.filename).parts)
        if info.is_dir():
            target.mkdir(parents=True, exist_ok=True)
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        with archive.open(info) as source_stream, target.open("wb") as target_stream:
            while chunk := source_stream.read(1024 * 1024):
                target_stream.write(chunk)
    print(next(iter(roots)))
PY

top_level="$(python3 - "$source_zip" <<'PY'
import pathlib, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as archive:
    roots = {pathlib.PurePosixPath(name.replace('\\', '/')).parts[0] for name in archive.namelist() if name}
print(next(iter(roots)))
PY
)"
source_root="$extracted/$top_level"

for required in gradlew gradlew.bat settings.gradle build.gradle.kts gradle.properties dependencies.gradle src/main/resources/mcmod.info src/main/java/com/gtnewhorizon/gtnhlib/GTNHLib.java; do
  test -f "$source_root/$required" || {
    printf 'ERROR: GTNHLib source archive lacks required file: %s\n' "$required" >&2
    exit 1
  }
done
chmod +x "$source_root/gradlew"

init_script="$temporary/print-reobf-output.gradle"
cat > "$init_script" <<'GRADLE'
gradle.projectsEvaluated {
    rootProject.tasks.register('wdgPrintReobfJar') {
        doLast {
            def reobf = rootProject.tasks.named('reobfJar').get()
            println('WDG_REOBF_JAR=' + reobf.archiveFile.get().asFile.absolutePath)
        }
    }
}
GRADLE

build_log="$output_directory/gtnhlib-0.11.31-build.log"
(
  cd "$source_root"
  VERSION="$expected_version" ./gradlew --no-daemon \
    --no-configuration-cache \
    --init-script "$init_script" \
    clean spotlessCheck test reobfJar wdgPrintReobfJar
) 2>&1 | tee "$build_log"

reobf_path="$(awk -F= '/^WDG_REOBF_JAR=/{value=substr($0,index($0,"=")+1)} END{print value}' "$build_log")"
test -n "$reobf_path" && test -f "$reobf_path" || {
  printf 'ERROR: GTNHLib reobfJar output was not reported or is missing.\n' >&2
  exit 1
}

case "$(basename "$reobf_path")" in
  *-dev.jar|*-dev-preshadow.jar|*-sources.jar|*-source.jar|*-api.jar|*-tests.jar)
    printf 'ERROR: classified/development GTNHLib output rejected: %s\n' "$reobf_path" >&2
    exit 1
    ;;
  *.jar) ;;
  *)
    printf 'ERROR: reobfJar output is not a JAR: %s\n' "$reobf_path" >&2
    exit 1
    ;;
esac

verified_output="$output_directory/gtnhlib-0.11.31.jar"
cp "$reobf_path" "$verified_output"

python3 - "$verified_output" <<'PY'
import json
import pathlib
import re
import sys
import zipfile

jar = pathlib.Path(sys.argv[1])
with zipfile.ZipFile(jar) as archive:
    names = archive.namelist()
    required = {
        "mcmod.info",
        "META-INF/MANIFEST.MF",
        "META-INF/gtnhlib_at.cfg",
        "META-INF/rfb-plugin/gtnhlib.properties",
        "mixins.gtnhlib.json",
        "mixins.gtnhlib.early.json",
        "com/gtnewhorizon/gtnhlib/GTNHLib.class",
        "com/gtnewhorizon/gtnhlib/core/GTNHLibCore.class",
        "com/gtnewhorizon/gtnhlib/core/GTNHLibCoreModContainer.class",
    }
    missing = sorted(required.difference(names))
    if missing:
        raise SystemExit(f"ERROR: GTNHLib production JAR lacks required members: {missing}")
    mcmod = archive.read("mcmod.info").decode("utf-8", "replace")
    metadata = json.loads(mcmod)
    if isinstance(metadata, list):
        entries = metadata
    elif isinstance(metadata, dict) and isinstance(metadata.get("modList"), list):
        entries = metadata["modList"]
    elif isinstance(metadata, dict):
        entries = [metadata]
    else:
        entries = []
    identity = next(
        (entry for entry in entries if isinstance(entry, dict) and entry.get("modid") == "gtnhlib"),
        None,
    )
    if not identity or identity.get("version") != "0.11.31" or identity.get("mcversion") != "1.7.10":
        raise SystemExit(f"ERROR: wrong GTNHLib identity: {identity}")
    manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
    if not re.search(r"(?im)^Multi-Release:\s*true\s*$", manifest):
        raise SystemExit("ERROR: GTNHLib production JAR is not Multi-Release: true")
    version_consumers = (
        archive.read("com/gtnewhorizon/gtnhlib/GTNHLib.class")
        + archive.read("com/gtnewhorizon/gtnhlib/core/GTNHLibCoreModContainer.class")
    )
    if b"0.11.31" not in version_consumers or any(
        marker in version_consumers for marker in (b"NO-GIT-TAG-SET", b"0.0.0", b"dirty")
    ):
        raise SystemExit("ERROR: GTNHLib inlined Tags.VERSION is not exactly 0.11.31")
    base = []
    versioned = []
    for name in names:
        if not name.endswith(".class"):
            continue
        data = archive.read(name)
        if data[:4] != b"\xca\xfe\xba\xbe":
            raise SystemExit(f"ERROR: malformed class: {name}")
        major = int.from_bytes(data[6:8], "big")
        (versioned if name.startswith("META-INF/versions/17/") else base).append(major)
    if not base or max(base) > 52:
        raise SystemExit(f"ERROR: GTNHLib base bytecode is not Java 8 compatible: {max(base, default=0)}")
    if not versioned or max(versioned) > 61 or 61 not in versioned:
        raise SystemExit(f"ERROR: GTNHLib Java 17 multi-release bytecode is invalid: {sorted(set(versioned))}")
PY

source_after="$(hash_file "$source_zip")"
[[ "$source_after" == "$source_before" ]] || {
  printf 'ERROR: original GTNHLib source ZIP changed during the build.\n' >&2
  exit 1
}

jar_sha256="$(hash_file "$verified_output")"
jar_size="$(file_size "$verified_output")"
report="$output_directory/gtnhlib-0.11.31-build-report.json"
python3 - "$report" "$source_zip" "$source_before" "$verified_output" "$jar_size" "$jar_sha256" <<'PY'
import json, pathlib, sys
report, source, source_hash, jar, size, jar_hash = sys.argv[1:]
data = {
    "schemaVersion": 1,
    "sourceArchive": pathlib.Path(source).name,
    "sourceSha256": source_hash,
    "versionOverride": "0.11.31",
    "productionTask": "reobfJar",
    "artifactFilename": pathlib.Path(jar).name,
    "artifactSize": int(size),
    "artifactSha256": jar_hash,
    "verified": True,
}
pathlib.Path(report).write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

printf '\nGTNHLib 0.11.31 production build PASSED\n'
printf 'Source ZIP: %s\n' "$source_zip"
printf 'Source SHA-256: %s\n' "$source_before"
printf 'Production JAR: %s\n' "$verified_output"
printf 'Production JAR bytes: %s\n' "$jar_size"
printf 'Production JAR SHA-256: %s\n' "$jar_sha256"
printf 'Machine-readable report: %s\n' "$report"
