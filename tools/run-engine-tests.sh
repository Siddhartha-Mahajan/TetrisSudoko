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
  "$PROJECT_DIR/app/src/main/java/org/secuso/privacyfriendlysudoku/controller/qqwing/GameDifficulty.java" \
  "$PROJECT_DIR/app/src/main/java/org/secuso/privacyfriendlysudoku/controller/qqwing/GameType.java" \
  "$PROJECT_DIR/app/src/main/java/org/secuso/privacyfriendlysudoku/controller/qqwing/LogItem.java" \
  "$PROJECT_DIR/app/src/main/java/org/secuso/privacyfriendlysudoku/controller/qqwing/LogType.java" \
  "$PROJECT_DIR/app/src/main/java/org/secuso/privacyfriendlysudoku/controller/qqwing/PrintStyle.java" \
  "$PROJECT_DIR/app/src/main/java/org/secuso/privacyfriendlysudoku/controller/qqwing/QQWing.java" \
  "$PROJECT_DIR/app/src/main/java/org/secuso/privacyfriendlysudoku/controller/qqwing/Symmetry.java" \
  "$PROJECT_DIR/tools/EngineSelfTest.java"
"$JDK_DIR/bin/java" -ea -cp "$OUTPUT_DIR" com.gridduo.app.EngineSelfTest
