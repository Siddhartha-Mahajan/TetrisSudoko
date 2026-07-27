# Security and privacy design

Grid Duo is deliberately offline and uses a very small attack surface.

- The manifest requests **no Android permissions**.
- The only exported component is the launcher activity required to open the app.
- There are no services, receivers, providers, deep links, or intent-based imports.
- There is no networking code, WebView, JavaScript, dynamic class loading, reflection,
  shell/process execution, native/JNI code, updater, analytics, advertising, or telemetry.
- Runtime state is limited to a private `SharedPreferences` file containing the theme,
  Sudoku progress, and the local Tetris high score.
- The app has no runtime library dependencies. QQWing is compiled from audited source
  included directly in this repository.
- The Gradle version and wrapper download checksum are pinned. SHA-256 checksums for
  all build-time plugin artifacts are recorded in `gradle/verification-metadata.xml`.

## Upstream source verification

The vendored QQWing Java files came from the repository and commit recorded in
`NOTICE.md`. At the time of incorporation:

- `GameDifficulty.java`
- `GameType.java`
- `LogItem.java`
- `LogType.java`
- `PrintStyle.java`
- `Symmetry.java`

were byte-for-byte identical to that commit.

`QQWing.java` has only five intentional behavior changes:

1. Naked pairs are rated `Moderate`, matching LibreSudoku.
2. Hidden pairs are rated `Moderate`, matching LibreSudoku.
3. Easy puzzles require more than 35 singles, matching LibreSudoku.
4. Array shuffling uses bounded `Random.nextInt` to avoid the `Integer.MIN_VALUE`
   edge case in `Math.abs`.
5. Random symmetry selection uses the same bounded approach.

The incorporated QQWing code imports only Java collection utilities and `Random`.

## Repeat the APK audit

After building, run:

```sh
./tools/audit-apk.sh app/build/outputs/apk/debug/app-debug.apk
```

The audit fails if it finds requested permissions, native libraries, networking,
WebView, dynamic loading, reflection, or process-execution APIs. It also verifies
the APK signature and prints a SHA-256 digest.

No finite review can prove that arbitrary software is defect-free, but these checks
make the offline boundary directly inspectable and reproducible.

