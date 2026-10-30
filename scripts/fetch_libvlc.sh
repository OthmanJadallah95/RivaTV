#!/usr/bin/env bash
# Fetches the prebuilt libVLC 3.0.13 for Android (native .so + Java bindings) from
# github.com/masterwok/libvlc-android-sdk and adapts the Java bindings to build without
# the Android support library. Run once before building.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK=${WORK:-$(mktemp -d)}
REPO=https://github.com/masterwok/libvlc-android-sdk

if [ ! -d "$WORK/vlc/.git" ]; then
  git clone -q --depth 1 "$REPO" "$WORK/vlc"
fi
SRC="$WORK/vlc/libvlc/src/main"

rm -rf "$ROOT/app/src/main/jniLibs" "$ROOT/app/src/main/java/org/videolan"
mkdir -p "$ROOT/app/src/main/jniLibs" "$ROOT/app/src/main/java/org"
cp -r "$SRC/jniLibs/." "$ROOT/app/src/main/jniLibs/"
cp -r "$SRC/java/org/videolan" "$ROOT/app/src/main/java/org/"

python3 - "$ROOT/app/src/main/java/org/videolan" <<'PY'
import pathlib, re, sys
for p in pathlib.Path(sys.argv[1]).rglob("*.java"):
    s = p.read_text()
    s = re.sub(r"^import android\.support\.annotation\.\w+;\n", "", s, flags=re.M)
    s = s.replace("android.support.v4.util.LongSparseArray", "android.util.LongSparseArray")
    s = re.sub(r"@RequiresApi\([^)]*\)\s*", "", s)
    s = re.sub(r"@(MainThread|NonNull|Nullable|WorkerThread|UiThread)\b\s*", "", s)
    # Don't kill the app when the native library can't load: let the caller fall back.
    s = s.replace('System.exit(1);', 'sLoaded = false; throw new IllegalStateException("libvlc", ule_or_se);')
    s = s.replace('catch (UnsatisfiedLinkError ule) {', 'catch (UnsatisfiedLinkError ule_or_se) {')
    s = s.replace('catch (SecurityException se) {', 'catch (SecurityException ule_or_se) {')
    s = s.replace('Log.e(TAG, "Can\'t load vlcjni library: " + ule);', 'Log.e(TAG, "Can\'t load vlcjni library: " + ule_or_se);')
    s = s.replace('Log.e(TAG, "Encountered a security issue when loading vlcjni library: " + se);', 'Log.e(TAG, "Encountered a security issue when loading vlcjni library: " + ule_or_se);')
    p.write_text(s)
PY
echo "libVLC ready: $(ls "$ROOT/app/src/main/jniLibs" | tr '\n' ' ')"
