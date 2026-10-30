#!/usr/bin/env bash
# Builds the APK with the bare Android tools (no Gradle / Android Studio needed).
# Needs: JDK 17+, and in $TOOLS: android.jar (API 29+), aapt2, dx.jar, apksigner.jar
# and libVLC fetched with scripts/fetch_libvlc.sh
set -euo pipefail
TOOLS=${TOOLS:-$PWD/tools}
OUT=build
SRC=app/src/main
rm -rf $OUT && mkdir -p $OUT/res $OUT/gen $OUT/classes $OUT/dex

# Gradle takes the package from build.gradle; aapt2 needs it in the manifest.
sed 's#<manifest #<manifest package="com.gguard.tv" #' $SRC/AndroidManifest.xml > $OUT/AndroidManifest.xml
"$TOOLS/aapt2" compile --dir $SRC/res -o $OUT/res.zip
"$TOOLS/aapt2" link -I "$TOOLS/android.jar" --manifest $OUT/AndroidManifest.xml \
  --min-sdk-version 21 --target-sdk-version 29 --version-code 1 --version-name 1.0.0 \
  --rename-manifest-package com.riva.tv \
  --java $OUT/gen -o $OUT/unsigned.apk $OUT/res.zip --auto-add-overlay

find $SRC/java $OUT/gen -name '*.java' > $OUT/sources.txt
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$TOOLS/android.jar" -d $OUT/classes @$OUT/sources.txt

# dx (built from AOSP dalvik/dx sources) turns the Java classes into classes.dex.
java -jar "$TOOLS/dx.jar" --dex --min-sdk-version=21 --output=$OUT/dex/classes.dex $OUT/classes

(cd $OUT/dex && zip -q ../unsigned.apk classes.dex)

KS=${KEYSTORE:-$TOOLS/gguard.keystore}
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass gguard123 -keypass gguard123 -alias gguard \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Riva TV" >/dev/null 2>&1
fi

# One APK per CPU family keeps the download small (libVLC is ~30 MB per ABI).
#   RivaTV-arm.apk -> TV screens and boxes (ARM);  RivaTV-x86.apk -> PC emulators (LDPlayer etc.)
package_variant() {
  local name=$1; shift
  cp $OUT/unsigned.apk $OUT/$name-unsigned.apk
  python3 - $OUT/$name-unsigned.apk $SRC/jniLibs "$@" <<'PY'
import os, sys, zipfile
apk, libs, abis = sys.argv[1], sys.argv[2], sys.argv[3:]
with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    for abi in abis:
        d = os.path.join(libs, abi)
        for f in sorted(os.listdir(d)):
            z.write(os.path.join(d, f), "lib/%s/%s" % (abi, f))
PY
  python3 "$(dirname "$0")/scripts/zipalign.py" $OUT/$name-unsigned.apk $OUT/$name-aligned.apk
  java -jar "$TOOLS/apksigner.jar" sign --ks "$KS" --ks-pass pass:gguard123 --ks-key-alias gguard \
    --min-sdk-version 21 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out $OUT/$name.apk $OUT/$name-aligned.apk
  java -jar "$TOOLS/apksigner.jar" verify --min-sdk-version 21 $OUT/$name.apk
  echo "Built $OUT/$name.apk ($(du -h $OUT/$name.apk | cut -f1))"
}

if [ ! -d $SRC/jniLibs ]; then
  echo "libVLC missing: run scripts/fetch_libvlc.sh first" >&2; exit 1
fi
package_variant RivaTV-arm armeabi-v7a arm64-v8a
package_variant RivaTV-x86 x86 x86_64
