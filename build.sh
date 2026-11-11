#!/bin/bash
# 一键构建脚本（Linux + Android SDK）
set -e

JDK=/home/user/android-build/jdk-11.0.2
SDK=/home/user/android-build/sdk
BT=$SDK/build-tools/30.0.3
AJ=$SDK/platforms/android-30/android.jar
THIS=$(cd "$(dirname "$0")" && pwd)

cd "$THIS"
rm -rf build
mkdir -p build/gen build/obj build/dex

echo "[1/5] 编译资源"
"$BT/aapt2" compile --dir res -o build/res.zip

echo "[2/5] 链接资源 + 生成 R.java"
"$BT/aapt2" link \
  -o build/unsigned.apk \
  -I "$AJ" \
  --manifest AndroidManifest.xml \
  -R build/res.zip \
  --java build/gen \
  --min-sdk-version 24 \
  --target-sdk-version 30 \
  --auto-add-overlay

echo "[3/5] 编译 Java"
"$JDK/bin/javac" -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" -d build/obj \
  src/com/xiaozhan/yyT/Prefs.java \
  src/com/xiaozhan/yyT/MainActivity.java \
  src/com/xiaozhan/yyT/AppListActivity.java \
  src/com/xiaozhan/yyT/LockActivity.java \
  src/com/xiaozhan/yyT/LockOverlay.java \
  src/com/xiaozhan/yyT/MonitorActivity.java \
  src/com/xiaozhan/yyT/LockAccessibilityService.java \
  src/com/xiaozhan/yyT/GuardForegroundService.java \
  src/com/xiaozhan/yyT/ConfigProvider.java \
  src/com/xiaozhan/yyT/Config.java \
  build/gen/com/xiaozhan/yyT/R.java

echo "[4/5] 生成 dex"
"$JDK/bin/jar" cf build/classes.jar -C build/obj .
JAVA_HOME="$JDK" PATH="$JDK/bin:$PATH" "$BT/d8" --lib "$AJ" --min-api 24 --output build/dex build/classes.jar

cp build/unsigned.apk build/final.apk
(cd build/dex && zip -q -X ../final.apk classes.dex)
"$BT/zipalign" -f 4 build/final.apk build/aligned.apk

echo "[5/5] 签名"
if [ ! -f build/debug.keystore ]; then
  keytool -genkeypair -keystore build/debug.keystore -storetype JKS \
    -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

"$BT/apksigner" sign \
  --ks build/debug.keystore --ks-pass pass:android \
  --ks-key-alias androiddebugkey --key-pass pass:android \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out build/应用锁.apk build/aligned.apk

"$BT/apksigner" verify build/应用锁.apk
echo "OK -> $THIS/build/应用锁.apk"
