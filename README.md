# Grid Duo

Grid Duo is a tiny, fully offline Android app containing:

- Algorithmically generated 9×9 Sudoku puzzles with one solution
- Easy, Medium, Hard, and Expert difficulty levels
- Notes, erasing, immediate mistake highlighting, three hints, and saved progress
- A complete falling-block game with the seven standard tetrominoes, a shuffled bag,
  rotation, wall kicks, soft/hard drop, line clears, score, levels, and increasing speed
- Light and dark themes

The app is implemented with the Android platform APIs only. It has no network
permission, advertisements, analytics, account, or third-party runtime dependency.

## Build

With JDK 17 and Android SDK 35 installed:

```sh
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release-unsigned.apk`.

