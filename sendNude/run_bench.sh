#!/usr/bin/env bash
# Benchmark: Traditional I/O vs NIO, 1 worker vs 10 workers, N runs each.
#
# Usage:  ./run_bench.sh <file-to-download> [runs=3] [port=9090]
#
# The write method (part files+merge  OR  final file by offset) is the one selected in Client.java
# ("CHANGE HERE" in runOnce()).  To compare both write methods: run this script, change the method
# name in Client.java, run it again.
set -euo pipefail

FILE="${1:?usage: ./run_bench.sh <file-to-download> [runs=3] [port=9090]}"
RUNS="${2:-3}"
PORT="${3:-9090}"

HERE="$(cd "$(dirname "$0")" && pwd)"
ABS="$(cd "$(dirname "$FILE")" && pwd)/$(basename "$FILE")"
NAME="$(basename "$FILE")"
WORK="$(mktemp -d)"
SERVER_PID=""
cleanup() { [ -n "$SERVER_PID" ] && kill "$SERVER_PID" 2>/dev/null || true; rm -rf "$WORK"; }
trap cleanup EXIT

mkdir -p "$WORK/shared" "$WORK/out" "$WORK/dl"
ln -s "$ABS" "$WORK/shared/$NAME"                       # serve the file without copying it
javac -d "$WORK/out" "$HERE/Server.java" "$HERE/Client.java"

for MODE in trad nio; do
  java -cp "$WORK/out" Server "$PORT" "$WORK/shared" "$MODE" > "$WORK/server_$MODE.log" 2>&1 &
  SERVER_PID=$!
  sleep 1
  for WORKERS in 1 10; do
    java -cp "$WORK/out" Client localhost "$PORT" download "$NAME" "$WORK/dl" "$WORKERS" "$MODE" "$RUNS" \
      | grep -E '^(Run|Summary)'
  done
  kill "$SERVER_PID"; wait "$SERVER_PID" 2>/dev/null || true; SERVER_PID=""
done
