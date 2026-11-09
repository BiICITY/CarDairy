#!/usr/bin/env bash
# The Pleos emulator sometimes boots with eth0 unconfigured, which crash-loops its vehicle
# services and blanks the screen. Reboots until eth0 has an address.
ADB="${ADB:-/c/Users/sob50/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
pause() { ping -n $(( $1 + 1 )) 127.0.0.1 >/dev/null; }
for attempt in 1 2 3 4; do
  for i in $(seq 1 40); do
    s=$(timeout 10 "$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    [ "$s" = "1" ] && break
    pause 5
  done
  pause 20
  if timeout 15 "$ADB" shell "ip -o -4 addr" 2>/dev/null | grep -q eth0; then
    pause 45
    echo "healthy on attempt $attempt: $(timeout 15 "$ADB" shell uptime)"
    exit 0
  fi
  echo "eth0 down on attempt $attempt, rebooting"
  timeout 30 "$ADB" reboot
  pause 30
done
echo "still unhealthy"
exit 1
