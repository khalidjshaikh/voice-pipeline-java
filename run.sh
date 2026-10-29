#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

JAR="target/voice-pipeline-1.0.0-SNAPSHOT.jar"

if [[ ! -f "$JAR" ]]; then
  echo "Jar not found. Running build first..."
  ./build.sh
fi

if [[ -z "${OPENAI_API_KEY:-}" ]]; then
  echo "ERROR: OPENAI_API_KEY is not set." >&2
  echo "  export OPENAI_API_KEY=sk-..." >&2
  exit 2
fi

echo "==> Starting voice pipeline..."
exec java -jar "$JAR" "$@"
