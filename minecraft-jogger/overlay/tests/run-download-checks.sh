#!/usr/bin/env bash
set -euo pipefail
jogger_jdk="${1:?Usage: run-download-checks.sh JDK_HOME JSON_JAR}"
jogger_json="${2:?Pass an org.json implementation jar}"
jogger_root="$(cd "$(dirname "$0")/.." && pwd)"
jogger_classes="$(mktemp -d)"
trap 'rm -rf "$jogger_classes"' EXIT
"$jogger_jdk/bin/javac" --release 8 -cp "$jogger_json" -d "$jogger_classes" \
  "$jogger_root/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/JoggerDownloads.java" \
  "$jogger_root/tests/JoggerDownloadsTest.java"
"$jogger_jdk/bin/java" -cp "$jogger_classes:$jogger_json" JoggerDownloadsTest
