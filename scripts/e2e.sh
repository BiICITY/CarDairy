#!/usr/bin/env bash
# End-to-end run on the headless Pleos emulator: drives real vehicle signals through the
# Virtual Console's HTTP API and saves a screenshot per scene. Usage: scripts/e2e.sh <outdir>
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-adb}"
OUT="${1:-$ROOT/out/e2e}"
B=http://127.0.0.1:8088
PKG=app.woorichadiary
export MSYS_NO_PATHCONV=1
mkdir -p "$OUT"; rm -f "$OUT"/*.png "$OUT/stop"; : > "$OUT/steps.txt"

pause() { ping -n $(( $1 + 1 )) 127.0.0.1 >/dev/null; }
shot() { timeout 25 "$ADB" exec-out screencap -p > "$OUT/$1.png"; echo "shot $1 $(date +%T)" >> "$OUT/steps.txt"; }
put() { curl -s -m 15 -X PUT "$B/vehicle/$1" -o /dev/null -w "$1 [%{http_code}]\n" >> "$OUT/steps.txt"; pause "${2:-6}"; }
tap() { timeout 15 "$ADB" shell input tap "$1" "$2"; pause "${3:-4}"; }

timeout 15 "$ADB" forward tcp:8088 tcp:8088 >/dev/null
( while [ ! -f "$OUT/stop" ]; do curl -s -m 3 -X POST "$B/browser/session?client=e2e" >/dev/null; pause 1; done ) &
pause 4

# 1. First-run setup through the real UI (ASCII because adb cannot type Korean).
timeout 60 "$ADB" shell pm clear --user current $PKG >> "$OUT/steps.txt" 2>&1
timeout 30 "$ADB" shell am start --user current -n $PKG/.ui.MainActivity >/dev/null 2>&1
pause 10
tap 2060 485 3; timeout 15 "$ADB" shell input text "Kong"; pause 2
pause 1
tap 2060 590 3; timeout 15 "$ADB" shell input text "Hajun"; pause 2
pause 1
shot 01_setup_filled
tap 1687 838 2
tap 2060 940 6
shot 02_home_after_setup

# 2. Korean family and yesterday's log for the real scenes.
timeout 15 "$ADB" shell am force-stop --user current $PKG
YDAY=$(date -d "$(timeout 15 "$ADB" shell date +%F | tr -d '
') -1 day" +%F)
BASE=$(( ($(date +%s) / 10) % 500000 + 1000 ))
printf '{"family":{"carName":"콩이","riders":{"REAR_LEFT":"하준"},"consent":true},"logs":{"%s":{"distanceKm":12.0,"rain":true,"riders":["하준"],"charged":false,"waitedMinutes":0,"outing":null}}}' "$YDAY" > "$OUT/diary.json"
timeout 20 "$ADB" push "$(cygpath -w "$OUT/diary.json")" /data/local/tmp/diary.json >/dev/null 2>&1
timeout 20 "$ADB" shell "run-as $PKG --user 10 sh -c 'cp /data/local/tmp/diary.json files/diary.json'" >> "$OUT/steps.txt" 2>&1
timeout 30 "$ADB" shell am start --user current -n $PKG/.ui.MainActivity >/dev/null 2>&1
pause 10
put "seat?kind=occupied&value=false&area=rear_left" 6
put "odometer?traveled_distance_km=$BASE" 4
put "ev-battery?field=soc&value=60" 4
shot 03_idle

# 3. Boarding: greeting with yesterday's diary.
put "seat?kind=occupied&value=true&area=rear_left" 8
shot 04_greeting

# 4. Driving: hidden, rain and distance recorded silently.
put "gear?gear=Drive" 6
shot 05_driving_hidden
put "body?front_wiper=Low" 4
put "odometer?traveled_distance_km=$((BASE + 12))" 6
put "ev-battery?field=soc&value=18" 6
put "gear?gear=Park" 8
timeout 30 "$ADB" shell am start --user current -n $PKG/.ui.MainActivity >/dev/null 2>&1
pause 5
shot 06_parked_battery_alert

# 5. Charging thanks.
put "ev-battery?field=soc&value=80" 6
shot 07_charged

# 6. Waiting then the outing question.
put "seat?kind=occupied&value=false&area=rear_left" 65
put "seat?kind=occupied&value=true&area=rear_left" 8
shot 08_ask_outing
tap 1670 572 5
shot 09_answered
tap 990 1120 4
shot 10_belt_hug
tap 2328 225 4
shot 11_demo_panel
timeout 20 "$ADB" shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1
timeout 20 "$ADB" exec-out cat /data/local/tmp/ui.xml > "$OUT/ui.xml"

touch "$OUT/stop"
timeout 30 "$ADB" logcat -d > "$OUT/all.txt"
timeout 20 "$ADB" shell "run-as $PKG --user 10 cat files/diary.json" > "$OUT/diary_after.json" 2>&1
echo "done $(date +%T)" >> "$OUT/steps.txt"
