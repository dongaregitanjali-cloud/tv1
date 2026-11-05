#!/bin/sh
# OPTIONAL and reversible. Disables the stock home's recommendation/ad rows for your user only.
# Usage: ./tools/optional-hide-stock-home.sh <tv-ip>            (disable)
#        ./tools/optional-hide-stock-home.sh <tv-ip> enable     (undo)
TV="${1:?usage: <tv-ip> [enable]}"
case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac
CMD=disable-user; [ "$2" = "enable" ] && CMD=enable
adb connect "$TV"
for p in com.google.android.tvrecommendations com.google.android.leanbacklauncher.recommendations; do
  adb -s "$TV" shell pm $CMD --user 0 $p || true
done
adb disconnect "$TV"
