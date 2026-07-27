#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JDK_DIR="$PROJECT_DIR/.toolchain/jdk/Contents/Home"
BUILD_TOOLS="$PROJECT_DIR/.toolchain/android-sdk/build-tools/35.0.0"
APK_PATH=${1:-"$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"}

case "$APK_PATH" in
  /*) ;;
  *) APK_PATH="$PROJECT_DIR/$APK_PATH" ;;
esac

if [ ! -f "$APK_PATH" ]; then
  echo "APK not found: $APK_PATH" >&2
  exit 1
fi

permission_dump=$("$BUILD_TOOLS/aapt" dump permissions "$APK_PATH")
if printf '%s\n' "$permission_dump" | grep -q 'uses-permission'; then
  echo "Unexpected Android permission found:" >&2
  printf '%s\n' "$permission_dump" >&2
  exit 1
fi

if unzip -l "$APK_PATH" | grep -qE 'lib/[^/]+/.*\\.so$'; then
  echo "Unexpected native library found in APK" >&2
  exit 1
fi

if rg -n -i \
  'java\\.net|android\\.webkit|WebView|Runtime\\.getRuntime|ProcessBuilder|DexClassLoader|PathClassLoader|System\\.load|System\\.loadLibrary|Class\\.forName|java\\.lang\\.reflect' \
  "$PROJECT_DIR/app/src/main/java"; then
  echo "Potentially unsafe API reference found in app source" >&2
  exit 1
fi

if rg -n \
  '<uses-permission|<service|<receiver|<provider' \
  "$PROJECT_DIR/app/src/main/AndroidManifest.xml"; then
  echo "Unexpected manifest capability found" >&2
  exit 1
fi

env JAVA_HOME="$JDK_DIR" "$BUILD_TOOLS/apksigner" verify --verbose "$APK_PATH"
shasum -a 256 "$APK_PATH"
echo "APK audit passed: zero permissions, zero native libraries, and no unsafe APIs."

