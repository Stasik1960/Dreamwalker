#!/bin/bash
# Run from the stopped/started server's normal working directory. This command
# observes the running JVM; it does not change mods, properties or world data.
set -euo pipefail
pid="${1:?Usage: bash capture-profile.sh JAVA_PID}"
[[ "$pid" =~ ^[0-9]+$ ]] || exit 2
java_bin="$(readlink -f "/proc/$pid/exe")"
jcmd="${JCMD:-$(dirname "$java_bin")/jcmd}"
if [[ ! -x "$jcmd" ]]; then
  echo 'jcmd is missing beside Java. Set JCMD to the matching JDK jcmd executable.' >&2
  exit 2
fi
out="$(pwd)/dw-profile-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir "$out"
"$jcmd" "$pid" VM.version > "$out/java.txt"
"$jcmd" "$pid" VM.flags > "$out/flags.txt"
grep -E '^(view-distance|simulation-distance|level-name|network-compression-threshold|sync-chunk-writes)=' server.properties > "$out/settings.txt"
find mods -maxdepth 1 -type f -name '*.jar' -print0 | sort -z | xargs -0 sha256sum > "$out/mods.sha256"
cat "/proc/$pid/status" > "$out/process-before.txt"
cat "/proc/$pid/cgroup" > "$out/cgroup.txt"
"$jcmd" "$pid" GC.heap_info > "$out/heap-before.txt"
"$jcmd" "$pid" VM.native_memory summary > "$out/native-before.txt" 2>&1 || true
"$jcmd" "$pid" JFR.start name=dw_streaming settings=profile duration=120s "filename=$out/server.jfr" dumponexit=true
echo 'Move normally through dense city districts for 90 seconds; keep player count and route fixed.'
sleep 90
echo 'Leave the measured district now; allow its chunks to unload for the final 30 seconds.'
sleep 31
"$jcmd" "$pid" GC.heap_info > "$out/heap-after-unload.txt"
"$jcmd" "$pid" VM.native_memory summary > "$out/native-after-unload.txt" 2>&1 || true
cat "/proc/$pid/status" > "$out/process-after-unload.txt"
if [[ -f /sys/fs/cgroup/memory.max ]]; then
  cat /sys/fs/cgroup/memory.max > "$out/cgroup-memory-max.txt"
  cat /sys/fs/cgroup/memory.current > "$out/cgroup-memory-current.txt"
fi
echo "Profile complete: $out"
echo 'Also retain the server log covering this interval and record the route and number of players.'
