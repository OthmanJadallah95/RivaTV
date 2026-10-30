# Riva TV — IPTV player for Android TV (built for G-Guard smart screens)

An original IPTV player for TV remotes, written in plain Java on the Android framework
(no external libraries). Playback uses **libVLC** (big network buffer, auto-reconnect, its own decoders) and falls back
to the TV's built-in player automatically if VLC can't open a stream.

## Features
- Login with **Xtream Codes API** (server + username + password) or an **M3U URL**
- Multiple playlists, remembers the last one
- **Live TV**: categories, channel list with logos, full-screen player,
  Up/Down or CH+/CH- to switch channels, Now/Next EPG
- **Movies** and **Series** (seasons → episodes, auto-plays next episode)
- Search, favorites (long-press OK or the Menu key), account status and expiry
- Live format setting (.ts or .m3u8) with automatic fallback to .m3u8
- Picture size: Menu/Info key cycles Fit / Stretch / Zoom
- Arabic and English UI (follows the device language), RTL support
- Shows in the Android TV launcher (leanback banner) and on phones/boxes

## Build the APK

### Option 0 — bare tools (`build.sh`)
First run `scripts/fetch_libvlc.sh` (downloads libVLC 3.0.13 for Android). Then:
Needs JDK 17+ and a tools folder with `android.jar` (API 29+), `aapt2` and `dx.jar`:
`TOOLS=/path/to/tools ./build.sh` → `build/RivaTV-arm.apk` (TVs/boxes) and `build/RivaTV-x86.apk` (PC emulators such as LDPlayer).

### Option A — Android Studio (on your PC)
1. Install Android Studio (Ladybug or newer).
2. Run `scripts/fetch_libvlc.sh` once (Git Bash/WSL on Windows), then File → Open → choose the folder. Wait for Gradle sync.
3. Build → Build App Bundle(s) / APK(s) → Build APK(s).
4. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

Command line equivalent: `./gradlew assembleRelease`

### Option B — GitHub (no Android Studio needed)
1. Create a new GitHub repository and upload this folder.
2. Open the **Actions** tab → "Build APK" runs automatically.
3. When it finishes, download the **RivaTV-apk** artifact (a zip with the APK).

## Install on the screen
- Copy the APK to a USB stick, plug it into the screen and open it with a file manager
  (allow "Install unknown apps" for the file manager), **or**
- Use the "Send Files to TV" app, **or**
- With developer mode + ADB: `adb connect <TV-IP>` then `adb install app-release.apk`

## Remote control
| Screen | Key | Action |
|---|---|---|
| Lists | OK | Open / play |
| Lists | Long-press OK or Menu | Add/remove favorite |
| Live player | Up / CH+ | Next channel |
| Live player | Down / CH- | Previous channel |
| Live player | Left / Right | Show channel info |
| Player | OK | Show controls |
| Player | Menu / Info | Fit / Stretch / Zoom |

## Notes
- `build.sh` signs with a self-made key; Gradle release uses the debug key. Both install directly.
  Use your own keystore before distributing it.
- Playback uses the TV's built-in MediaPlayer, so supported codecs depend on the screen.
  Live TV defaults to HLS (.m3u8) and falls back to .ts automatically (Settings tile to change).
- Change the name, package (`com.gguard.tv`), colors (`res/values/colors.xml`)
  and icons (`res/mipmap-*`, `res/drawable-xhdpi/banner.png`) to brand it.

## Third-party
- **libVLC** 3.0.x by VideoLAN, LGPL-2.1 — https://code.videolan.org/videolan/vlc-android .
  Prebuilt binaries and Java bindings taken from https://github.com/masterwok/libvlc-android-sdk
  (`scripts/fetch_libvlc.sh`). The bindings are modified only to drop Android support-library
  annotations and to throw instead of exiting when the native library can't load.
