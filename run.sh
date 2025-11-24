#!/bin/bash

set -e

# Create build directory
mkdir -p build/classes

# Setup LWJGL classpath
LWJGL_CP="."
if [ -d "lib/lwjgl" ]; then
  LWJGL_CP=".:lib/lwjgl/*"
fi

# Compile
echo "Compiling sources..."
SOURCES=$(find src/main/java -type f -name "*.java")
javac -d build/classes -cp "$LWJGL_CP" $SOURCES

# Setup Runtime Classpath
LWJGL_DIR="lib/lwjgl"
CLASSPATH="build/classes"
if [ -d "$LWJGL_DIR" ]; then
  CLASSPATH="$CLASSPATH:$LWJGL_DIR/*"
fi

# Setup Native Library Path
OS="$(uname -s)"
ARCH_RAW="$(uname -m)"

case "$OS" in
  Darwin*) OS_ID="macos";;
  Linux*) OS_ID="linux";;
  MINGW*|MSYS*|CYGWIN*|Windows*) OS_ID="windows";;
  *) OS_ID="";;
esac

case "$ARCH_RAW" in
  arm64|aarch64) ARCH_ID="arm64";;
  x86_64) ARCH_ID="x86_64";;
  *) ARCH_ID="";;
esac

JAVA_FLAGS=""
if [ -n "$OS_ID" ] && [ -n "$ARCH_ID" ] && [ -d "$LWJGL_DIR/natives/${OS_ID}-${ARCH_ID}" ]; then
  JAVA_FLAGS="-Dorg.lwjgl.librarypath=$LWJGL_DIR/natives/${OS_ID}-${ARCH_ID}"
fi

echo "Starting Game..."
if [[ "$OS" == Darwin* ]]; then
  exec java -XstartOnFirstThread $JAVA_FLAGS -cp "$CLASSPATH" com.gameengine.example.Main
else
  exec java $JAVA_FLAGS -cp "$CLASSPATH" com.gameengine.example.Main
fi
