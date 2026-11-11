#!/usr/bin/env bash
# Prepares the emulator for a photo test: seeds an outing that ended just now and opens the QR
# screen. Prints the phone page address, reachable from the PC through adb forward.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-adb}"
OUT="${1:-$ROOT/out/photo}"
PKG=app.woorichadiary
export MSYS_NO_PATHCONV=1
mkdir -p "$OUT"
pause() { ping -n $(( $1 + 1 )) 127.0.0.1 >/dev/null; }

for i in $(seq 1 40); do s=$(timeout 10 "$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r'); [ "$s" = "1" ] && break; pause 5; done
timeout 200 "$ADB" install -r -g "$(cygpath -w $ROOT/app/build/outputs/apk/debug/app-debug.apk)" | tail -1

NOW=$(( $(date +%s) * 1000 ))
H=3600000
# FUTURE_HOURS keeps the outing open so photos taken during a phone test still match.
T0=$(( NOW - 7 * H )); T1=$(( NOW - 6 * H )); T2=$(( NOW + (${FUTURE_HOURS:-0} - 1) * H )); T3=$(( T2 + H ))
TODAY=$(timeout 15 "$ADB" shell date +%F | tr -d '\r')
printf '{"family":{"carName":"콩이","riders":{"REAR_LEFT":"하준"},"consent":true},"logs":{"%s":{"distanceKm":0,"rain":false,"riders":["하준"],"charged":false,"waitedMinutes":300,"outing":"바다","windows":[{"kind":"IN_CAR","start":%s,"end":%s},{"kind":"DRIVING","start":%s,"end":%s},{"kind":"WAITING","start":%s,"end":%s},{"kind":"IN_CAR","start":%s,"end":%s}]}}}' \
  "$TODAY" $T0 $T1 $T0 $T1 $T1 $T2 $T2 $T3 > "$OUT/diary.json"
timeout 15 "$ADB" shell am force-stop --user current $PKG
timeout 20 "$ADB" push "$(cygpath -w "$OUT/diary.json")" /data/local/tmp/diary.json >/dev/null 2>&1
timeout 20 "$ADB" shell "run-as $PKG --user 10 sh -c 'mkdir -p files; cp /data/local/tmp/diary.json files/diary.json; rm -rf files/photos'"
timeout 30 "$ADB" shell am start --user current -S -n $PKG/.ui.MainActivity >/dev/null 2>&1
pause 30
timeout 15 "$ADB" forward tcp:8765 tcp:8765 >/dev/null

find_tap() {
  timeout 25 "$ADB" shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1
  timeout 20 "$ADB" exec-out cat /data/local/tmp/ui.xml > "$OUT/ui.xml"
  python -I "$(cygpath -w $ROOT/scripts/find_text.py)" "$(cygpath -w "$OUT/ui.xml")" "$1"
}
p=""; for i in 1 2 3 4 5 6; do p=$(find_tap "사진 고르기"); [ -n "$p" ] && break; pause 10; done; echo "photo button: $p"
[ -n "$p" ] && timeout 15 "$ADB" shell input tap $p
pause 5
timeout 25 "$ADB" exec-out screencap -p > "$OUT/01_qr.png"
timeout 25 "$ADB" shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1
timeout 20 "$ADB" exec-out cat /data/local/tmp/ui.xml > "$OUT/ui.xml"
grep -oE 'http://[0-9.]+:8765/\?t=[0-9a-f]+' "$OUT/ui.xml" | head -1 | sed -E 's#http://[0-9.]+#http://127.0.0.1#' | tee "$OUT/url.txt"
