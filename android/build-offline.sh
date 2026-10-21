#!/bin/sh
# Builds with no internet using ./.gradle-offline (made by prepare-offline.sh)
set -e
cd "$(dirname "$0")"
[ -d .gradle-offline/wrapper/dists ] || { echo "Run ./prepare-offline.sh once with internet first"; exit 1; }
java -version 2>&1 | grep -q 'version "17' || { echo "JDK 17 is required (set JAVA_HOME)"; exit 1; }
export GRADLE_USER_HOME="$(pwd)/.gradle-offline"
./gradlew --offline assembleDebug "$@"
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
