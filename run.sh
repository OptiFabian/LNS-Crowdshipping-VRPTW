#!/usr/bin/env sh
# Build and run the LNS. Usage: ./run.sh [--customers N] [--routes] [instance ...]   e.g. ./run.sh c107 c108
# With no instance names, runs the default set c107, c108, c109.
# Optional: SEED=42 ./run.sh c107   for a reproducible run.
set -e
cd "$(dirname "$0")"
# Use the JDK in JAVA_HOME if set; otherwise javac/java must be on PATH.
JAVAC=javac
JAVA=java
if [ -n "$JAVA_HOME" ]; then
  jh="$JAVA_HOME"
  if command -v cygpath >/dev/null 2>&1; then jh="$(cygpath -u "$JAVA_HOME")"; fi
  JAVAC="$jh/bin/javac"
  JAVA="$jh/bin/java"
fi
mkdir -p build
"$JAVAC" -d build *.java
if [ -n "$SEED" ]; then
  "$JAVA" -Dseed="$SEED" -cp build App "$@"
else
  "$JAVA" -cp build App "$@"
fi
