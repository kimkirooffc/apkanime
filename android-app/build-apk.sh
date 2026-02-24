#!/bin/bash
# Build script for AniFlow Android APK

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== AniFlow Android Build ==="
echo ""

# Force Java 17 on Termux if available.
JAVA17_HOME="/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk"
if [ -d "$JAVA17_HOME" ]; then
    export JAVA_HOME="$JAVA17_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "JAVA_HOME: $JAVA_HOME"
fi

# Default Android SDK location for Termux when env vars are not set.
DEFAULT_ANDROID_SDK="/data/data/com.termux/files/home/apkanime/android-sdk"
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    export ANDROID_HOME="$DEFAULT_ANDROID_SDK"
    export ANDROID_SDK_ROOT="$DEFAULT_ANDROID_SDK"
fi

ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export ANDROID_HOME ANDROID_SDK_ROOT

if [ ! -d "$ANDROID_SDK_ROOT" ]; then
    echo "ERROR: Android SDK directory not found: $ANDROID_SDK_ROOT"
    exit 1
fi

echo "Android SDK: $ANDROID_SDK_ROOT"

# Check for required SDK components
if [ ! -d "$ANDROID_SDK_ROOT/platform-tools" ]; then
    echo "WARNING: platform-tools not found"
fi

# Prefer Termux aapt2, fallback to SDK build-tools aapt2.
TERMUX_AAPT2="/data/data/com.termux/files/usr/bin/aapt2"
if [ -x "$TERMUX_AAPT2" ]; then
    AAPT2_BIN="$TERMUX_AAPT2"
else
    AAPT2_BIN="$ANDROID_SDK_ROOT/build-tools/34.0.0/aapt2"
fi

if [ ! -x "$AAPT2_BIN" ]; then
    echo "ERROR: aapt2 executable not found."
    echo "Checked:"
    echo "  $TERMUX_AAPT2"
    echo "  $ANDROID_SDK_ROOT/build-tools/34.0.0/aapt2"
    exit 1
fi

echo "AAPT2 binary: $AAPT2_BIN"

# Make gradlew executable
if [ -f "./gradlew" ]; then
    chmod +x gradlew
else
    echo "gradlew not found. Make sure you have the Gradle wrapper."
    exit 1
fi

# Build
echo ""
echo "Building APK..."
echo ""

./gradlew --no-daemon -Pandroid.aapt2FromMavenOverride="$AAPT2_BIN" clean assembleDebug

echo ""
echo "=== Build Complete ==="
echo ""
echo "APK location: app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "To install on device:"
echo "  adb install app/build/outputs/apk/debug/app-debug.apk"
echo ""
