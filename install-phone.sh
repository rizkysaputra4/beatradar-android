#!/usr/bin/env bash
# Builds the debug app against the test VPS and installs it on the phone connected by adb (USB or wireless debugging).
#
#   ./install-phone.sh                  test, build, install and open the app
#   ./install-phone.sh --skip-tests     skip the unit tests
#   ./install-phone.sh --local          point the app at this PC's server (http://<Wi-Fi IP>:8080/) instead
#   API=http://host/ ./install-phone.sh any other server
#
# Wireless debugging (Android 11+): Developer options → Wireless debugging → Pair device with pairing code, then
#   adb pair <ip>:<pair-port> <code>   and   adb connect <ip>:<port>
# A phone that was paired once usually reconnects by itself (mDNS) when both are on the same Wi-Fi.
set -euo pipefail
cd "$(dirname "$0")"

API=${API:-https://api.findbeatradar.com/}
run_tests=1
for arg in "$@"; do
  case $arg in
    --skip-tests) run_tests=0 ;;
    --local)
      ip=$(ipconfig | grep -A20 -i "wi-fi" | grep -m1 "IPv4" | sed 's/.*: //' | tr -d '\r')
      [[ -n $ip ]] || { echo "no Wi-Fi IPv4 address found" >&2; exit 1; }
      API="http://$ip:8080/" ;;
    *) echo "ignoring option for another script: $arg" >&2 ;;
  esac
done

step() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

sdk=$(grep '^sdk.dir=' local.properties | cut -d= -f2- | sed 's/\\:/:/; s/\\\\/\//g')
ADB="$sdk/platform-tools/adb.exe"
[[ -x $ADB ]] || ADB=$(command -v adb) || { echo "adb not found (sdk.dir in local.properties)" >&2; exit 1; }

step "Phone"
devices=$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
[[ -n $devices ]] || { echo "No phone connected. Plug in USB or turn on Wireless debugging (see the top of this file)." >&2; exit 1; }
if [[ $(wc -l <<<"$devices") -gt 1 && -z ${ANDROID_SERIAL:-} ]]; then
  echo "Several devices; pick one with ANDROID_SERIAL=<serial>:" >&2; echo "$devices" >&2; exit 1
fi
export ANDROID_SERIAL=${ANDROID_SERIAL:-$devices}
echo "$("$ADB" shell getprop ro.product.model | tr -d '\r') ($ANDROID_SERIAL), server $API"

if [[ $run_tests == 1 ]]; then
  step "Unit tests"
  ./gradlew :app:testDebugUnitTest --console=plain -q
fi

step "Build and install"
./gradlew :app:installDebug -PapiBaseUrl="$API" --console=plain -q

step "Open the app"
"$ADB" shell am start -n com.boimin.beatradar/.MainActivity >/dev/null
echo "installed and opened"
