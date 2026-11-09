#!/usr/bin/env bash
# Records the car-screen scenes of the demo video on the headless emulator, driving real vehicle
# signals through the Virtual Console. Usage: record_scenes.sh <outdir> part1|part2
ADB="${ADB:-/c/Users/sob50/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
OUT="$1"; PART="$2"
B=http://127.0.0.1:8088
PKG=app.woorichadiary
export MSYS_NO_PATHCONV=1
mkdir -p "$OUT"
pause() { ping -n $(( $1 + 1 )) 127.0.0.1 >/dev/null; }
sh_() { timeout 25 "$ADB" shell "$@" 2>/dev/null; }
tap() { sh_ input tap "$1" "$2"; pause "${3:-1}"; }
put() { curl -s -m 15 -X PUT "$B/vehicle/$1" -o /dev/null -w "$1 [%{http_code}]\n" >> "$OUT/steps.txt"; }
say() { echo "$(date +%T) $*" | tee -a "$OUT/steps.txt"; }

# rec <name> <seconds>: records in the background; pull with finish <name> after it ends.
rec() { timeout $(( $2 + 20 )) "$ADB" shell screenrecord --time-limit "$2" --size 1280x720 --bit-rate 6000000 "/data/local/tmp/$1.mp4" >/dev/null 2>&1 & REC=$!; pause 2; say "rec $1"; }
finish() { wait $REC; timeout 60 "$ADB" exec-out cat "/data/local/tmp/$1.mp4" > "$OUT/$1.mp4"; sh_ rm "/data/local/tmp/$1.mp4"; say "saved $1 $(wc -c < "$OUT/$1.mp4")"; }

# The car shell can leave navigation on top right after boot, so retry until the app is in front.
front() {
  for i in 1 2 3 4 5 6 7 8; do
    sh_ am start --user current -S -n $PKG/.ui.MainActivity >/dev/null
    pause 15
    if sh_ dumpsys activity activities | grep topResumedActivity | head -1 | grep -q woorichadiary; then
      pause 8
      say "app in front after $i tries"
      return 0
    fi
  done
  say "app never came to front"
  return 1
}

timeout 15 "$ADB" forward tcp:8088 tcp:8088 >/dev/null
timeout 15 "$ADB" forward tcp:8765 tcp:8765 >/dev/null
rm -f "$OUT/stop"
( while [ ! -f "$OUT/stop" ]; do curl -s -m 3 -X POST "$B/browser/session?client=rec" >/dev/null; pause 1; done ) &
pause 3

if [ "$PART" = part1 ]; then
  timeout 200 "$ADB" install -r -g "$(cygpath -w /c/Users/sob50/WooriChaDiary/app/build/outputs/apk/debug/app-debug.apk)" | tail -1
  sh_ pm clear --user current $PKG >/dev/null
  pause 2
  sh_ am start --user current -S -n $PKG/.ui.MainActivity >/dev/null
  pause 25

  rec s1_setup 40
  tap 2060 485 2
  # 콩이 on the car's Korean keyboard
  for k in "720 1137" "1210 1028" "883 1028" "883 1028" "1537 1028"; do tap $k 1; done
  pause 1
  tap 2060 590 2
  # 하준
  for k in "1102 1028" "1429 1028" "666 920" "1265 1137" "775 1028"; do tap $k 1; done
  pause 1
  sh_ input keyevent 4; pause 3
  tap 2060 838 2
  tap 2063 941 3
  finish s1_setup
  timeout 25 "$ADB" exec-out screencap -p > "$OUT/check_setup.png"

  # Yesterday's log so the greeting has a diary to read.
  sh_ am force-stop --user current $PKG
  TODAY=$(timeout 15 "$ADB" shell date +%F | tr -d '\r')
  YDAY=$(date -d "$TODAY -1 day" +%F)
  printf '{"family":{"carName":"콩이","riders":{"REAR_LEFT":"하준"},"consent":true},"logs":{"%s":{"distanceKm":12.0,"rain":true,"riders":["하준"],"charged":false,"waitedMinutes":0,"outing":null,"windows":[]}}}' "$YDAY" > "$OUT/diary.json"
  timeout 20 "$ADB" push "$(cygpath -w "$OUT/diary.json")" /data/local/tmp/diary.json >/dev/null 2>&1
  sh_ "run-as $PKG --user 10 sh -c 'cp /data/local/tmp/diary.json files/diary.json'"
  put "seat?kind=occupied&value=false&area=rear_left"
  BASE=$(( ($(date +%s) / 10) % 500000 + 1000 ))
  put "odometer?traveled_distance_km=$BASE"
  put "ev-battery?field=soc&value=60"
  put "body?front_wiper=Off"
  pause 3
  sh_ am start --user current -S -n $PKG/.ui.MainActivity >/dev/null
  pause 20

  rec s2_board 16
  pause 3; put "seat?kind=occupied&value=true&area=rear_left"
  finish s2_board

  rec s3_belt 9
  pause 2; tap 990 1120 1
  finish s3_belt

  rec s4_drive 14
  pause 2; put "gear?gear=Drive"; pause 3
  put "body?front_wiper=Low"; pause 2
  put "odometer?traveled_distance_km=$((BASE + 12))"; pause 1
  put "ev-battery?field=soc&value=18"
  finish s4_drive

  rec s5_park 12
  pause 2; put "gear?gear=Park"; pause 2
  sh_ am start --user current -n $PKG/.ui.MainActivity >/dev/null
  finish s5_park

  rec s6_charge 9
  pause 2; put "ev-battery?field=soc&value=80"
  finish s6_charge

  put "seat?kind=occupied&value=false&area=rear_left"
  say "waiting for the outing"
  pause 65

  rec s7_ask 15
  pause 2; put "seat?kind=occupied&value=true&area=rear_left"; pause 5
  tap 1670 572 1
  finish s7_ask
  timeout 25 "$ADB" exec-out screencap -p > "$OUT/check_ask.png"

  put "seat?kind=occupied&value=false&area=rear_left"
  pause 2
  rec s8_qr 8
  pause 2; tap 2226 226 1
  finish s8_qr
  timeout 30 "$ADB" shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1
  timeout 20 "$ADB" exec-out cat /data/local/tmp/ui.xml > "$OUT/ui.xml"
  grep -oE 'http://[0-9.]+:8765/\?t=[0-9a-f]+' "$OUT/ui.xml" | head -1 | sed -E 's#http://[0-9.]+#http://127.0.0.1#' > "$OUT/url.txt"
  say "url ready: $(wc -c < "$OUT/url.txt") bytes"
fi

if [ "$PART" = part2 ]; then
  rec s9_photos 14
  pause 2; put "seat?kind=occupied&value=true&area=rear_left"
  finish s9_photos
  timeout 25 "$ADB" exec-out screencap -p > "$OUT/check_photos.png"
fi

if [ "$PART" = retake ]; then
  sh_ pm clear --user current $PKG >/dev/null
  pause 2
  front || exit 1

  rec s1_setup 58
  # The keyboard takes several seconds to appear, and keys pressed before that are lost.
  tap 2060 485 7
  for k in "720 1137" "1210 1028" "883 1028" "883 1028" "1537 1028"; do tap $k 2; done
  tap 2060 590 4
  for k in "1102 1028" "1429 1028" "666 920" "1265 1137" "775 1028"; do tap $k 2; done
  pause 2
  sh_ input keyevent 4; pause 4
  tap 2060 838 2
  tap 2063 941 3
  finish s1_setup
  timeout 25 "$ADB" exec-out screencap -p > "$OUT/check_setup.png"

  sh_ am force-stop --user current $PKG
  sh_ "run-as $PKG --user 10 sh -c 'cp /data/local/tmp/diary.json files/diary.json'"
  put "seat?kind=occupied&value=false&area=rear_left"
  pause 3
  front || exit 1
  put "seat?kind=occupied&value=true&area=rear_left"
  pause 4
  put "seat?kind=occupied&value=false&area=rear_left"
  say "waiting for the outing"
  pause 65

  rec s7_ask 28
  pause 2; put "seat?kind=occupied&value=true&area=rear_left"; pause 3
  timeout 30 "$ADB" shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1
  timeout 20 "$ADB" exec-out cat /data/local/tmp/ui.xml > "$OUT/ui.xml"
  P=$(python -I "$(cygpath -w "$(dirname "$0")/find_text.py")" "$(cygpath -w "$OUT/ui.xml")" "바다")
  say "answer at $P"
  [ -n "$P" ] && sh_ input tap $P
  finish s7_ask
  timeout 25 "$ADB" exec-out screencap -p > "$OUT/check_ask.png"
fi

touch "$OUT/stop"
say "done $PART"
