#!/usr/bin/env bash
# Builds the APK with the bare Android tools (no Gradle / Android Studio needed).
# Needs: JDK 17+, and in $TOOLS: android.jar (API 29+), aapt2, dx.jar, apksigner.jar
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
  --java $OUT/gen -o $OUT/unsigned.apk $OUT/res.zip --auto-add-overlay

find $SRC/java $OUT/gen -name '*.java' > $OUT/sources.txt
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$TOOLS/android.jar" -d $OUT/classes @$OUT/sources.txt

# dx (built from AOSP dalvik/dx sources) turns the Java classes into classes.dex.
java -jar "$TOOLS/dx.jar" --dex --min-sdk-version=21 --output=$OUT/dex/classes.dex $OUT/classes

(cd $OUT/dex && zip -q ../unsigned.apk classes.dex)

# Align stored entries, then sign with v1 + v2 + v3 (apksigner built from AOSP apksig sources).
python3 "$(dirname "$0")/scripts/zipalign.py" $OUT/unsigned.apk $OUT/aligned.apk

KS=${KEYSTORE:-$TOOLS/gguard.keystore}
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass gguard123 -keypass gguard123 -alias gguard \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=G-Guard TV" >/dev/null 2>&1
fi
java -jar "$TOOLS/apksigner.jar" sign --ks "$KS" --ks-pass pass:gguard123 --ks-key-alias gguard \
  --min-sdk-version 21 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out $OUT/GGuardTV.apk $OUT/aligned.apk
java -jar "$TOOLS/apksigner.jar" verify --min-sdk-version 21 $OUT/GGuardTV.apk
echo "Built $OUT/GGuardTV.apk"
