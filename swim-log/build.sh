#!/usr/bin/env bash
# 나의 수영 기록 - Gradle 없이 Android SDK 도구만으로 APK를 빌드하는 스크립트
#
# 필요 도구: javac(JDK 17+), aapt2, d8 또는 dx(dalvik-exchange), zipalign, apksigner
#   Ubuntu: apt install android-sdk-build-tools dalvik-exchange
# 필요 파일:
#   COMPILE_JAR : API 34 이상 android.jar  (Health Connect 내장 API 컴파일용)
#   LINK_JAR    : aapt2 가 읽을 수 있는 android.jar (리소스 링크용, 기본값은 COMPILE_JAR)
# 서명 키: KEYSTORE(기본 keystore/release.jks), KS_PASS, KEY_ALIAS
set -euo pipefail
cd "$(dirname "$0")"

COMPILE_JAR=${COMPILE_JAR:-$ANDROID_HOME/platforms/android-35/android.jar}
LINK_JAR=${LINK_JAR:-$COMPILE_JAR}
KEYSTORE=${KEYSTORE:-keystore/release.jks}
KS_PASS=${KS_PASS:-swimlog}
KEY_ALIAS=${KEY_ALIAS:-swimlog}
VERSION=$(grep -o 'android:versionName="[^"]*"' AndroidManifest.xml | cut -d'"' -f2)
OUT=build
APK=$OUT/swim-log-$VERSION.apk

rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes"

echo "▶ 리소스 컴파일"
aapt2 compile --dir res -o "$OUT/res.zip"
aapt2 link -I "$LINK_JAR" --manifest AndroidManifest.xml --java "$OUT/gen" \
  --min-sdk-version 26 --target-sdk-version 34 --version-code 1 --version-name "$VERSION" \
  -o "$OUT/unsigned.apk" "$OUT/res.zip"

echo "▶ Java 컴파일"
javac -nowarn -encoding UTF-8 --release 8 -Xlint:-options -cp "$COMPILE_JAR" -d "$OUT/classes" \
  $(find src "$OUT/gen" -name '*.java')

echo "▶ DEX 변환"
if command -v d8 >/dev/null; then
  d8 --min-api 26 --lib "$COMPILE_JAR" --output "$OUT" $(find "$OUT/classes" -name '*.class')
else
  dalvik-exchange --dex --min-sdk-version=26 --output="$OUT/classes.dex" "$OUT/classes"
fi
(cd "$OUT" && zip -q unsigned.apk classes.dex)

echo "▶ 정렬 · 서명"
zipalign -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=SwimLog" >/dev/null
fi
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KEY_ALIAS" --out "$APK" "$OUT/aligned.apk"
apksigner verify "$APK"
echo "✅ $APK ($(du -h "$APK" | cut -f1))"
