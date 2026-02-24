#!/bin/bash
# Build APK AniFlow di Termux (1 command)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$SCRIPT_DIR"

export ANDROID_HOME="$PROJECT_ROOT/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

JAVA17_HOME="/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk"
if [ -d "$JAVA17_HOME" ]; then
    export JAVA_HOME="$JAVA17_HOME"
fi

AAPT2_BIN="/data/data/com.termux/files/usr/bin/aapt2"
if [ ! -x "$AAPT2_BIN" ]; then
    AAPT2_BIN="$ANDROID_HOME/build-tools/34.0.0/aapt2"
fi

export PATH="$JAVA_HOME/bin:$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0"

echo "=== AniFlow Android Build ==="
echo "ANDROID_HOME: $ANDROID_HOME"
echo "JAVA_HOME: ${JAVA_HOME:-<default>}"
echo "AAPT2: $AAPT2_BIN"
echo ""

if [ ! -f "./gradlew" ]; then
    echo "ERROR: gradlew not found!"
    exit 1
fi

if [ ! -x "$AAPT2_BIN" ]; then
    echo "ERROR: aapt2 not found ($AAPT2_BIN)"
    exit 1
fi

chmod +x gradlew

echo "Building debug APK..."
./gradlew clean assembleDebug --no-daemon \
  -Pandroid.aapt2FromMavenOverride="$AAPT2_BIN"

APK_PATH="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
FINAL_APK="$PROJECT_ROOT/AniFlow-debug.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: APK not found at $APK_PATH"
    exit 1
fi

cp -f "$APK_PATH" "$FINAL_APK"

echo ""
echo "=== BUILD SUCCESS ==="
echo "APK Gradle : $APK_PATH"
echo "APK Final  : $FINAL_APK"
ls -lh "$APK_PATH" "$FINAL_APK"
echo ""
echo "Install (adb): adb install -r \"$FINAL_APK\""
