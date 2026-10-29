#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "==> Building voice-pipeline..."
mvn -q -o package "$@"
echo "==> Build complete: target/voice-pipeline-1.0.0-SNAPSHOT.jar"
