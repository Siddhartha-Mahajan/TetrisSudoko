# Grid Duo

Grid Duo is a tiny, fully offline Android app containing:

- Algorithmically generated 9×9 Sudoku puzzles with one solution
- QQWing technique-rated Easy, Medium, Hard, and Expert difficulty levels;
  Expert boards are capped at 24 starting clues
- Notes, erasing, immediate mistake highlighting, three hints, and saved progress
- A complete Tetris game with the seven standard tetrominoes, a shuffled bag,
  rotation, wall kicks, soft/hard drop, line clears, score, levels, increasing speed,
  pause/resume, and an always-visible new-game control
- Light and dark themes

The app is implemented with the Android platform APIs only. It has no network
permission, advertisements, analytics, account, or third-party runtime dependency.
The APK is intentionally drawn with one native custom view and contains no bundled
fonts, bitmap artwork, or remote assets.

## Build

With JDK 17 and Android SDK 35 installed:

```sh
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release-unsigned.apk`.

## Test

The dependency-free game-engine checks can be run after the project-local JDK
has been bootstrapped:

```sh
./tools/run-engine-tests.sh
```

They verify generated solutions, uniqueness, logical difficulty, notes, hints,
save/restore behavior, Tetris movement, hard-drop scoring, and line clearing.

## Source and license

Grid Duo is GPL-3.0-or-later because it selectively incorporates the QQWing
lineage used by LibreSudoku. See [NOTICE.md](NOTICE.md) for exact upstream
repositories, commits, mappings, and copyright notices.
