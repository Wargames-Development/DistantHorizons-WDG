#!/usr/bin/env bash
set -euo pipefail

log_file="${1:?Usage: collect-macos-rc-evidence.sh /path/to/latest.log [output-directory]}"
output="${2:-./validation-logs/change007-macos-evidence}"
mkdir -p "$output"

{
  echo "macOS release-candidate system evidence"
  sw_vers
  echo
  sysctl -n machdep.cpu.brand_string 2>/dev/null || true
  system_profiler SPHardwareDataType SPDisplaysDataType 2>/dev/null | sed -E 's/(Serial Number|Hardware UUID|Provisioning UDID):.*/\1: REDACTED/'
  echo
  echo "Relevant Java processes:"
  ps -axo pid,ppid,comm | grep '[j]ava' | sed -E 's#(/Users/)[^/]+#\1REDACTED#g' || true
} > "$output/system.txt"

grep -Ei 'java version|Temurin|lwjgl3ify|LWJGL|Distant Horizons|release candidate|build source|git commit|managed by the WDG|updater|worker|thread|LOD|SQLite|migration|save|shutdown' "$log_file" \
  | sed -E -e 's#(/Users/)[^/]+#\1REDACTED#g' -e 's#([Aa][Cc][Cc][Ee][Ss][Ss][_-]?[Tt][Oo][Kk][Ee][Nn]|[Oo][Aa][Uu][Tt][Hh]|[Bb][Ee][Aa][Rr][Ee][Rr]|[Aa][Uu][Tt][Hh][Oo][Rr][Ii][Zz][Aa][Tt][Ii][Oo][Nn])[=: ]+[^ ]+#\1=REDACTED#g' > "$output/relevant-log-lines.txt" || true

cat > "$output/manual-observations.txt" <<'TEMPLATE'
Allocated Java memory:
Initial Java 8 parent confirmed:
Packaged Java 21 child confirmed:
Selected runtime path/platform:
Warning shown as three separate lines:
Main menu:
World creation:
Initial LOD generation:
Initial worker count:
Render distance / generation mode / quality / thread preset:
FPS and frame-time observations:
CPU/GPU/thermal observations:
Save:
Reopen and generated-LOD reload:
Normal shutdown:
TEMPLATE

printf 'Evidence written to %s\n' "$output"
