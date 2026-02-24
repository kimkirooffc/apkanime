#!/bin/bash
# Wrapper build seperti AstroTools: output APK final di root folder project.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/android-app"

./build.sh
