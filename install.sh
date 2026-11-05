#!/bin/sh
# Usage: ./tools/install.sh <tv-ip> [path/to/app-release.apk] [path/to/config.json]
# Needs only `adb` on your PC. Nothing is left running on the TV.
set -e
TV="${1:?usage: install.sh <tv-ip> [apk] [config.json]}"
case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac
APK="${2:-app-release.apk}"
CFG="${3:-tools/config.json}"
PKG=com.example.tvlauncher
DEST=/sdcard/Android/data/$PKG/files

adb connect "$TV"
adb -s "$TV" install -r "$APK"
adb -s "$TV" shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION || true   # lets Android show the Wi-Fi name

# launch once so Android creates the app's files folder, then push config
adb -s "$TV" shell am start -n $PKG/.MainActivity
sleep 3
adb -s "$TV" shell mkdir -p $DEST
adb -s "$TV" push "$CFG" $DEST/config.json

# optional extras: ./tools/install.sh ip apk config  then:
#   adb -s $TV push Outfit-Regular.ttf $DEST/        (and set "fontFile": "Outfit-Regular.ttf")
#   adb -s $TV push wallpaper.jpg      $DEST/        (and set "wallpaper": "wallpaper.jpg")
#   adb -s $TV push config.kids.json   $DEST/        (extra profile; switch with the profile chip top right)

adb -s "$TV" shell cmd package set-home-activity $PKG/.MainActivity || echo "Pick the launcher manually when Android asks (press Home)."
adb disconnect "$TV"
echo "Done."
