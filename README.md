# TV Launcher (tvOS-style)

## Get the APK
1. Upload this whole folder (including the hidden `.github` folder) to a new GitHub repo.
2. Actions tab -> **Build APK** -> when green, download **tv-launcher-apk** (zip with `app-release.apk`).

## Install (from your PC, nothing stays running on the TV)
Enable Developer options + Network/USB debugging on the TV, then:

    ./tools/install.sh 192.168.1.50 app-release.apk tools/config.json

That installs the app, grants the Wi-Fi-name permission, pushes `config.json`, and sets it as Home.
Undo: `adb shell cmd package set-home-activity <stock-launcher>` or Settings > Apps > Default apps, then `adb uninstall com.example.tvlauncher`.
Optional, reversible ad-row removal for the stock home: `tools/optional-hide-stock-home.sh <ip>` (add `enable` to undo).

## Controls
- Left/Right: move through the dock. **Down**: all apps slide up. **Up** from the first row: back to the dock.
- **OK** opens an app. **Hold OK**: Open / App info (force stop, clear cache) / Pin / Hide / Uninstall.
- Sidebar (left): Home, AI assistant, Standby, System settings.
- Top right: HDMI input chips, profile chip (when 2+ profiles), Wi-Fi/Ethernet, weather, date, clock.

## config.json (in /sdcard/Android/data/com.example.tvlauncher/files/)
dock (package names; empty = recent apps first), hidden, wallpaper, fontFile, tilesPerRow (3-10), autoDark,
showStats, screensaverMinutes (0 = off), weather {lat, lon, fahrenheit}, geminiKey, chatModel, imageModel.
Profiles: push `config.<name>.json` next to it; tap the profile chip to cycle. Edits apply next time the launcher opens.
Font: push a .ttf (e.g. Outfit-Regular.ttf from fonts.google.com) and set "fontFile".
Find package names: `adb shell pm list packages -3`.

## Limits
- Force-stop / clear-cache are done on the system App info page (apps can't do it silently without system privileges).
- CPU load isn't readable by normal apps on modern Android; RAM and storage are shown.
- Which HDMI input is active can't be detected; chips just switch to an input.
- "Continue watching" inside other apps isn't readable; the dock shows your most recently opened apps instead.
- Standby/screensaver runs inside the launcher (it doesn't turn the panel off).
