#!/usr/bin/env bash
# Waits until the emulator is usable, brings the app forward and opens the QR screen.
# Prints the phone page address reachable from this PC.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-adb}"
OUT="${1:-$ROOT/out/photo}"
PKG=app.woorichadiary
export MSYS_NO_PATHCONV=1
mkdir -p "$OUT"
pause() { ping -n $(( $1 + 1 )) 127.0.0.1 >/dev/null; }

for i in $(seq 1 60); do
  s=$(timeout 10 "$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  e=$(timeout 10 "$ADB" shell "ip -o -4 addr" 2>/dev/null | grep -c eth0)
  [ "$s" = "1" ] && [ "$e" = "1" ] && break
  pause 5
done
pause 40
timeout 15 "$ADB" forward tcp:8765 tcp:8765 >/dev/null
timeout 40 "$ADB" shell am start --user current -S -n $PKG/.ui.MainActivity >/dev/null 2>&1
url=""
for i in 1 2 3 4 5 6 7 8 9 10; do
  pause 8
  timeout 15 "$ADB" shell input tap 2226 226 >/dev/null 2>&1
  pause 4
  timeout 30 "$ADB" shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1
  timeout 20 "$ADB" exec-out cat /data/local/tmp/ui.xml > "$OUT/ui.xml"
  url=$(grep -oE 'http://[0-9.]+:8765/\?t=[0-9a-f]+' "$OUT/ui.xml" | head -1 | sed -E 's#http://[0-9.]+#http://127.0.0.1#')
  [ -n "$url" ] && break
done
echo "$url" | tee "$OUT/url.txt"
