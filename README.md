# G-Guard TV — IPTV player for Android TV / G-Guard screens

An original IPTV player (Kotlin + Media3/ExoPlayer) built for TV remotes.

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

### Option A — Android Studio (on your PC)
1. Install Android Studio (Ladybug or newer).
2. File → Open → choose the `GGuardTV` folder. Wait for Gradle sync.
3. Build → Build App Bundle(s) / APK(s) → Build APK(s).
4. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

Command line equivalent: `./gradlew assembleRelease`
→ `app/build/outputs/apk/release/app-release.apk`

### Option B — GitHub (no Android Studio needed)
1. Create a new GitHub repository and upload this folder.
2. Open the **Actions** tab → "Build APK" runs automatically.
3. When it finishes, download the **GGuardTV-apk** artifact (a zip with the APK).

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
- The release build is signed with the debug key so it installs directly.
  Use your own keystore before distributing it.
- Some channels use AC3/EAC3 audio; if a TV lacks that decoder you'll get video
  without sound. Adding the Media3 FFmpeg extension fixes it (requires building it).
- Change the name, package (`com.gguard.tv`), colors (`res/values/colors.xml`)
  and icons (`res/mipmap-*`, `res/drawable-xhdpi/banner.png`) to brand it.
