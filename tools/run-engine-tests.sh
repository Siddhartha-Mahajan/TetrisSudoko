#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JDK_DIR="$PROJECT_DIR/.toolchain/jdk/Contents/Home"
OUTPUT_DIR="$PROJECT_DIR/build/engine-tests"

mkdir -p "$OUTPUT_DIR"
"$JDK_DIR/bin/javac" -Xlint:all -d "$OUTPUT_DIR" \
  "$PROJECT_DIR/app/src/main/java/com/gridduo/app/SudokuEngine.java" \
  "$PROJECT_DIR/app/src/main/java/com/gridduo/app/SudokuGame.java" \
  "$PROJECT_DIR/app/src/main/java/com/gridduo/app/TetrisEngine.java" \
  "$PROJECT_DIR/tools/EngineSelfTest.java"
"$JDK_DIR/bin/java" -ea -cp "$OUTPUT_DIR" com.gridduo.app.EngineSelfTest

