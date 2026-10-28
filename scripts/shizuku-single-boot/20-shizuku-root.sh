#!/system/bin/sh
# Single root boot owner for Shizuku. Native manager boot receiver is disabled.
(
  umask 077
  mkdir /dev/.shizuku-root-boot-once 2>/dev/null || exit 0
  exec > /data/adb/shizuku-root-boot.log 2>&1
  echo "begin uptime=$(cut -d' ' -f1 /proc/uptime)"
  attempt=0
  while [ "$attempt" -lt 120 ]; do
    apk=$(pm path moe.shizuku.privileged.api 2>/dev/null | head -1 | cut -d: -f2-)
    starter="${apk%/*}/lib/arm64/libshizuku.so"
    [ -n "$apk" ] && [ -x "$starter" ] && break
    attempt=$((attempt + 1))
    sleep 1
  done
  if [ -z "$apk" ] || [ ! -x "$starter" ]; then
    echo "Shizuku APK/starter unavailable; no start attempted"
    exit 1
  fi
  servers=$(pidof shizuku_server)
  set -- $servers
  if [ "$#" -eq 1 ] && [ "$(stat -c %u /proc/$1 2>/dev/null)" = 0 ]; then
    echo "existing single root server pid=$1; no duplicate start"
    exit 0
  fi
  echo "official starter uptime=$(cut -d' ' -f1 /proc/uptime)"
  "$starter" --apk="$apk"
  echo "finished uptime=$(cut -d' ' -f1 /proc/uptime)"
) &