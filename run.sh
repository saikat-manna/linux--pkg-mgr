#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="${SCRIPT_DIR}/target/linux-pkg-mgr-0.1.0-SNAPSHOT.jar"
JAVAFX_LIB="${HOME}/javafx-sdk-25.0.2/lib"
DEBUG_PORT="${DEBUG_PORT:-18000}"
SUSPEND="${SUSPEND:-n}"

echo "Starting with debug port ${DEBUG_PORT} (suspend=${SUSPEND})"

java \
    --module-path "${JAVAFX_LIB}" \
    --add-modules javafx.controls,javafx.graphics \
    -agentlib:jdwp=transport=dt_socket,server=y,suspend=${SUSPEND},address=*:${DEBUG_PORT} \
    -jar "${JAR}" \
    --dev \
    "$@"
