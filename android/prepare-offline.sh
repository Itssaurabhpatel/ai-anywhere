#!/bin/sh
# Run ONCE with internet: caches Gradle 8.2 + all dependencies in ./.gradle-offline
set -e
cd "$(dirname "$0")"
java -version 2>&1 | grep -q 'version "17' || { echo "JDK 17 is required (set JAVA_HOME)"; exit 1; }
export GRADLE_USER_HOME="$(pwd)/.gradle-offline"
chmod +x gradlew
./gradlew --no-daemon assembleDebug
echo "Done. From now on: ./build-offline.sh (no internet needed)"
