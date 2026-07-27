package com.gridduo.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class GameView extends View {
    private static final int HOME = 0;
    private static final int LEVELS = 1;
    private static final int SUDOKU = 2;
    private static final int TETRIS = 3;

    private static final int A_THEME = 1;
    private static final int A_OPEN_SUDOKU = 2;
    private static final int A_OPEN_TETRIS = 3;
    private static final int A_BACK = 4;
    private static final int A_DIFFICULTY = 5;
    private static final int A_SUDOKU_CELL = 6;
    private static final int A_NUMBER = 7;
    private static final int A_ERASE = 8;
    private static final int A_NOTES = 9;
    private static final int A_HINT = 10;
    private static final int A_NEW_SUDOKU = 11;
    private static final int A_CONTINUE_SUDOKU = 12;
    private static final int A_T_LEFT = 20;
    private static final int A_T_RIGHT = 21;
    private static final int A_T_ROTATE = 22;
    private static final int A_T_SOFT = 23;
    private static final int A_T_HARD = 24;
    private static final int A_T_PAUSE = 25;
    private static final int A_T_RESTART = 26;

    private static final String PREFS = "grid_duo";
    private static final String KEY_DARK = "dark";
    private static final String KEY_SUDOKU = "sudoku";
    private static final String KEY_HIGH_SCORE = "tetris_high_score";

    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<HitTarget> hits = new ArrayList<>();
    private final Random random = new Random();
    private final ExecutorService puzzleExecutor = Executors.newSingleThreadExecutor();
    private final SharedPreferences preferences;

    private Palette palette;
    private boolean darkTheme;
    private int screen = HOME;
    private SudokuGame sudoku;
    private TetrisEngine tetris;
    private int selectedCell = -1;
    private boolean noteMode;
    private boolean generating;
    private int generationToken;
    private long sudokuLastTick;
    private long tetrisLastFrame;
    private long tetrisDropAccumulator;
    private int tetrisHighScore;
    private boolean hostPaused;
    private String toastMessage;
    private long toastUntil;

    GameView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        darkTheme = preferences.getBoolean(KEY_DARK, false);
        tetrisHighScore = preferences.getInt(KEY_HIGH_SCORE, 0);
        palette = new Palette(darkTheme);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        setFocusable(true);
        setContentDescription("Grid Duo: offline Sudoku and falling-block games");

        String saved = preferences.getString(KEY_SUDOKU, null);
        if (saved != null) {
            sudoku = SudokuGame.decode(saved);
        }
    }

    boolean isDarkTheme() {
        return darkTheme;
    }

    void onHostResume() {
        hostPaused = false;
        sudokuLastTick = SystemClock.elapsedRealtime();
        tetrisLastFrame = SystemClock.elapsedRealtime();
        invalidate();
    }

    void onHostPause() {
        hostPaused = true;
        saveState();
    }

    void onHostDestroy() {
        puzzleExecutor.shutdownNow();
    }

    boolean goBack() {
        if (screen == HOME) {
            return false;
        }
        if (screen == SUDOKU) {
            saveSudoku();
            screen = LEVELS;
        } else {
            if (screen == TETRIS && tetris != null && !tetris.gameOver) {
                tetris.paused = true;
            }
            screen = HOME;
        }
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        hits.clear();
        canvas.drawColor(palette.background);

        if (screen == HOME) {
            drawHome(canvas);
        } else if (screen == LEVELS) {
            drawLevelPicker(canvas);
        } else if (screen == SUDOKU) {
            updateSudokuClock();
            drawSudoku(canvas);
        } else {
            updateTetris();
            drawTetris(canvas);
        }

        drawToast(canvas);
    }

    private void drawHome(Canvas canvas) {
        float width = getWidth();
        float side = dp(20);

        text(canvas, "GRID DUO", width / 2, dp(52), sp(30), palette.text, true,
                Paint.Align.CENTER);
        text(canvas, "Two classics. One tiny offline app.", width / 2, dp(77), sp(14),
                palette.muted, false, Paint.Align.CENTER);

        RectF themeRect = new RectF(width - dp(58), dp(16), width - dp(14), dp(60));
        roundRect(canvas, themeRect, dp(16), palette.card);
        drawThemeIcon(canvas, themeRect.centerX(), themeRect.centerY());
        hit(themeRect, A_THEME, 0);

        float top = dp(112);
        float available = getHeight() - top - dp(76);
        float gap = dp(16);
        float cardHeight = Math.min(dp(220), (available - gap) / 2);
        cardHeight = Math.max(dp(154), cardHeight);

        RectF sudokuCard = new RectF(side, top, width - side, top + cardHeight);
        gameCard(canvas, sudokuCard, palette.blue, "SUDOKU", "9 × 9",
                "Unique puzzles • notes • hints", true);
        hit(sudokuCard, A_OPEN_SUDOKU, 0);

        RectF tetrisCard = new RectF(side, sudokuCard.bottom + gap, width - side,
                sudokuCard.bottom + gap + cardHeight);
        gameCard(canvas, tetrisCard, palette.orange, "BLOCK DROP", "10 × 20",
                "Seven pieces • lines • levels", false);
        hit(tetrisCard, A_OPEN_TETRIS, 0);

        text(canvas, "NO ADS  •  NO INTERNET  •  NO PERMISSIONS", width / 2,
                getHeight() - dp(24), sp(10), palette.muted, true, Paint.Align.CENTER);
    }

    private void gameCard(Canvas canvas, RectF rect, int accent, String title, String size,
                          String description, boolean sudokuIcon) {
        shadowCard(canvas, rect);
        RectF accentBar = new RectF(rect.left, rect.top, rect.left + dp(7), rect.bottom);
        canvas.save();
        canvas.clipRect(rect);
        canvas.drawRoundRect(accentBar, dp(3), dp(3), fill(accent));
        canvas.restore();

        float iconSize = Math.min(dp(88), rect.height() * 0.52f);
        RectF icon = new RectF(rect.left + dp(24), rect.centerY() - iconSize / 2,
                rect.left + dp(24) + iconSize, rect.centerY() + iconSize / 2);
        if (sudokuIcon) {
            drawSudokuIcon(canvas, icon, accent);
        } else {
            drawBlocksIcon(canvas, icon, accent);
        }

        float textX = icon.right + dp(22);
        text(canvas, title, textX, rect.centerY() - dp(25), sp(22), palette.text, true,
                Paint.Align.LEFT);
        text(canvas, size, textX, rect.centerY(), sp(13), accent, true, Paint.Align.LEFT);
        text(canvas, description, textX, rect.centerY() + dp(28), sp(12), palette.muted,
                false, Paint.Align.LEFT);
        text(canvas, "›", rect.right - dp(24), rect.centerY() + dp(8), sp(34),
                palette.muted, false, Paint.Align.CENTER);
    }

    private void drawSudokuIcon(Canvas canvas, RectF rect, int color) {
        roundRect(canvas, rect, dp(10), withAlpha(color, 26));
        strokePaint.setColor(color);
        strokePaint.setStrokeWidth(dp(1));
        for (int i = 1; i < 3; i++) {
            float x = rect.left + rect.width() * i / 3;
            float y = rect.top + rect.height() * i / 3;
            canvas.drawLine(x, rect.top + dp(9), x, rect.bottom - dp(9), strokePaint);
            canvas.drawLine(rect.left + dp(9), y, rect.right - dp(9), y, strokePaint);
        }
        text(canvas, "9", rect.left + rect.width() / 6, rect.top + rect.height() * 0.30f,
                sp(15), color, true, Paint.Align.CENTER);
        text(canvas, "4", rect.centerX(), rect.centerY() + dp(5), sp(15), color, true,
                Paint.Align.CENTER);
        text(canvas, "7", rect.left + rect.width() * 5 / 6,
                rect.top + rect.height() * 0.82f, sp(15), color, true, Paint.Align.CENTER);
    }

    private void drawBlocksIcon(Canvas canvas, RectF rect, int color) {
        roundRect(canvas, rect, dp(10), withAlpha(color, 26));
        float cell = rect.width() / 5;
        int[][] cells = {{1, 1}, {2, 1}, {2, 2}, {3, 2}, {1, 3}, {2, 3}, {3, 3}};
        for (int[] item : cells) {
            RectF block = new RectF(rect.left + item[0] * cell,
                    rect.top + item[1] * cell,
                    rect.left + (item[0] + 1) * cell - dp(2),
                    rect.top + (item[1] + 1) * cell - dp(2));
            roundRect(canvas, block, dp(3), color);
        }
    }

    private void drawLevelPicker(Canvas canvas) {
        float width = getWidth();
        drawBack(canvas, "Sudoku");
        text(canvas, "CHOOSE YOUR LEVEL", dp(20), dp(96), sp(24), palette.text, true,
                Paint.Align.LEFT);
        text(canvas, "Every puzzle is 9 × 9 with exactly one solution.", dp(20), dp(122),
                sp(13), palette.muted, false, Paint.Align.LEFT);

        float y = dp(154);
        if (sudoku != null && !sudoku.complete) {
            RectF resume = new RectF(dp(20), y, width - dp(20), y + dp(62));
            roundRect(canvas, resume, dp(16), withAlpha(palette.blue, darkTheme ? 40 : 24));
            text(canvas, "CONTINUE " + sudoku.difficulty.label.toUpperCase(Locale.US),
                    resume.left + dp(16), resume.top + dp(25), sp(13), palette.blue, true,
                    Paint.Align.LEFT);
            text(canvas, sudoku.formattedTime() + "  •  " + sudoku.hintsRemaining + " hints left",
                    resume.left + dp(16), resume.top + dp(46), sp(12), palette.muted, false,
                    Paint.Align.LEFT);
            text(canvas, "›", resume.right - dp(22), resume.centerY() + dp(7), sp(28),
                    palette.blue, false, Paint.Align.CENTER);
            hit(resume, A_CONTINUE_SUDOKU, 0);
            y += dp(78);
        }

        SudokuEngine.Difficulty[] levels = SudokuEngine.Difficulty.values();
        float gap = dp(12);
        float remaining = getHeight() - y - dp(24);
        float itemHeight = Math.min(dp(112), (remaining - gap * 3) / 4);
        itemHeight = Math.max(dp(76), itemHeight);

        for (int i = 0; i < levels.length; i++) {
            SudokuEngine.Difficulty difficulty = levels[i];
            RectF rect = new RectF(dp(20), y, width - dp(20), y + itemHeight);
            shadowCard(canvas, rect);
            int accent = difficultyAccent(i);
            roundRect(canvas, new RectF(rect.left + dp(14), rect.centerY() - dp(20),
                    rect.left + dp(54), rect.centerY() + dp(20)), dp(12),
                    withAlpha(accent, darkTheme ? 48 : 30));
            text(canvas, String.valueOf(i + 1), rect.left + dp(34), rect.centerY() + dp(7),
                    sp(18), accent, true, Paint.Align.CENTER);
            text(canvas, difficulty.label, rect.left + dp(70), rect.centerY() - dp(4),
                    sp(18), palette.text, true, Paint.Align.LEFT);
            text(canvas, difficulty.description, rect.left + dp(70), rect.centerY() + dp(20),
                    sp(12), palette.muted, false, Paint.Align.LEFT);
            text(canvas, "›", rect.right - dp(22), rect.centerY() + dp(7), sp(28),
                    palette.muted, false, Paint.Align.CENTER);
            hit(rect, A_DIFFICULTY, i);
            y += itemHeight + gap;
        }
    }

    private void drawSudoku(Canvas canvas) {
        drawBack(canvas, sudoku == null ? "Sudoku" : sudoku.difficulty.label);
        RectF newRect = new RectF(getWidth() - dp(72), dp(8), getWidth() - dp(10), dp(48));
        pill(canvas, newRect, "NEW", palette.card, palette.muted, sp(11));
        hit(newRect, A_NEW_SUDOKU, 0);

        if (generating || sudoku == null) {
            drawGenerating(canvas);
            return;
        }

        float top = dp(60);
        float statWidth = (getWidth() - dp(40)) / 3;
        stat(canvas, dp(14) + statWidth * 0, top, statWidth, "TIME", sudoku.formattedTime());
        stat(canvas, dp(14) + statWidth * 1, top, statWidth, "MISTAKES",
                String.valueOf(sudoku.mistakes));
        stat(canvas, dp(14) + statWidth * 2, top, statWidth, "HINTS",
                sudoku.hintsRemaining + " / 3");

        float boardTop = dp(104);
        float boardSize = Math.min(getWidth() - dp(24),
                getHeight() - boardTop - dp(248));
        boardSize = Math.max(dp(270), boardSize);
        float boardLeft = (getWidth() - boardSize) / 2;
        drawSudokuBoard(canvas, boardLeft, boardTop, boardSize);

        float controlsTop = boardTop + boardSize + dp(14);
        float side = dp(14);
        float actionGap = dp(8);
        float actionWidth = (getWidth() - side * 2 - actionGap * 2) / 3;
        float actionHeight = dp(48);

        actionButton(canvas, new RectF(side, controlsTop, side + actionWidth,
                        controlsTop + actionHeight), "⌫", "ERASE", false, A_ERASE);
        actionButton(canvas, new RectF(side + actionWidth + actionGap, controlsTop,
                        side + actionWidth * 2 + actionGap, controlsTop + actionHeight),
                "✎", "NOTES", noteMode, A_NOTES);
        actionButton(canvas, new RectF(side + (actionWidth + actionGap) * 2, controlsTop,
                        getWidth() - side, controlsTop + actionHeight),
                "◇", "HINT", false, A_HINT);

        float numberTop = controlsTop + actionHeight + dp(14);
        float numberGap = dp(4);
        float numberWidth = (getWidth() - side * 2 - numberGap * 8) / 9;
        float numberHeight = Math.min(dp(58), getHeight() - numberTop - dp(12));
        for (int number = 1; number <= 9; number++) {
            float left = side + (number - 1) * (numberWidth + numberGap);
            RectF rect = new RectF(left, numberTop, left + numberWidth,
                    numberTop + numberHeight);
            roundRect(canvas, rect, dp(10), palette.card);
            text(canvas, String.valueOf(number), rect.centerX(), rect.centerY() + dp(8),
                    sp(22), palette.blue, true, Paint.Align.CENTER);
            hit(rect, A_NUMBER, number);
        }

        if (sudoku.complete) {
            drawSudokuComplete(canvas);
        }
    }

    private void drawGenerating(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f - dp(16);
        strokePaint.setColor(palette.blue);
        strokePaint.setStrokeWidth(dp(5));
        strokePaint.setStyle(Paint.Style.STROKE);
        RectF arc = new RectF(cx - dp(24), cy - dp(24), cx + dp(24), cy + dp(24));
        canvas.drawArc(arc, -70, 265, false, strokePaint);
        text(canvas, "Building a unique puzzle…", cx, cy + dp(58), sp(15), palette.text,
                true, Paint.Align.CENTER);
        text(canvas, "Generated entirely on your device", cx, cy + dp(80), sp(12),
                palette.muted, false, Paint.Align.CENTER);
        postInvalidateDelayed(40);
    }

    private void drawSudokuBoard(Canvas canvas, float left, float top, float size) {
        float cellSize = size / 9f;
        RectF outer = new RectF(left, top, left + size, top + size);
        roundRect(canvas, outer, dp(5), palette.board);

        int selectedValue = selectedCell >= 0 ? sudoku.values[selectedCell] : 0;
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row * 9 + col;
                float x = left + col * cellSize;
                float y = top + row * cellSize;
                RectF cell = new RectF(x, y, x + cellSize, y + cellSize);

                if (selectedCell >= 0) {
                    int selectedRow = selectedCell / 9;
                    int selectedCol = selectedCell % 9;
                    boolean peer = row == selectedRow || col == selectedCol
                            || (row / 3 == selectedRow / 3 && col / 3 == selectedCol / 3);
                    if (peer) {
                        canvas.drawRect(cell, fill(palette.peer));
                    }
                    if (selectedValue != 0 && sudoku.values[index] == selectedValue) {
                        canvas.drawRect(cell, fill(palette.same));
                    }
                }
                if (sudoku.isMistake(index)) {
                    canvas.drawRect(cell, fill(palette.errorBackground));
                }
                if (index == selectedCell) {
                    canvas.drawRect(cell, fill(palette.selected));
                }

                int value = sudoku.values[index];
                if (value != 0) {
                    int color;
                    boolean bold;
                    if (sudoku.isMistake(index)) {
                        color = palette.red;
                        bold = true;
                    } else if (sudoku.puzzle[index] != 0) {
                        color = palette.text;
                        bold = true;
                    } else if (sudoku.hinted[index]) {
                        color = palette.green;
                        bold = true;
                    } else {
                        color = palette.blue;
                        bold = false;
                    }
                    text(canvas, String.valueOf(value), cell.centerX(),
                            cell.centerY() + cellSize * 0.26f, cellSize * 0.56f,
                            color, bold, Paint.Align.CENTER);
                } else if (sudoku.notes[index] != 0) {
                    drawNotes(canvas, cell, sudoku.notes[index]);
                }
                hit(cell, A_SUDOKU_CELL, index);
            }
        }

        strokePaint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i <= 9; i++) {
            boolean thick = i % 3 == 0;
            strokePaint.setStrokeWidth(thick ? dp(2.2f) : dp(0.7f));
            strokePaint.setColor(thick ? palette.gridHeavy : palette.gridLight);
            float position = left + i * cellSize;
            canvas.drawLine(position, top, position, top + size, strokePaint);
            position = top + i * cellSize;
            canvas.drawLine(left, position, left + size, position, strokePaint);
        }
    }

    private void drawNotes(Canvas canvas, RectF cell, int notes) {
        float sub = cell.width() / 3f;
        for (int number = 1; number <= 9; number++) {
            if ((notes & (1 << (number - 1))) == 0) {
                continue;
            }
            int row = (number - 1) / 3;
            int col = (number - 1) % 3;
            text(canvas, String.valueOf(number), cell.left + sub * (col + 0.5f),
                    cell.top + sub * (row + 0.72f), sub * 0.58f, palette.muted,
                    false, Paint.Align.CENTER);
        }
    }

    private void drawSudokuComplete(Canvas canvas) {
        canvas.drawColor(withAlpha(Color.BLACK, darkTheme ? 145 : 100));
        float width = getWidth() - dp(48);
        RectF dialog = new RectF(dp(24), getHeight() / 2f - dp(132),
                dp(24) + width, getHeight() / 2f + dp(132));
        roundRect(canvas, dialog, dp(24), palette.card);
        text(canvas, "PUZZLE COMPLETE", dialog.centerX(), dialog.top + dp(52), sp(22),
                palette.green, true, Paint.Align.CENTER);
        text(canvas, sudoku.difficulty.label + "  •  " + sudoku.formattedTime(),
                dialog.centerX(), dialog.top + dp(84), sp(14), palette.text, false,
                Paint.Align.CENTER);
        text(canvas, sudoku.mistakes + (sudoku.mistakes == 1 ? " mistake" : " mistakes")
                        + "  •  " + (3 - sudoku.hintsRemaining) + " hints used",
                dialog.centerX(), dialog.top + dp(108), sp(12), palette.muted, false,
                Paint.Align.CENTER);
        RectF again = new RectF(dialog.left + dp(20), dialog.bottom - dp(72),
                dialog.right - dp(20), dialog.bottom - dp(20));
        pill(canvas, again, "NEW PUZZLE", palette.blue, Color.WHITE, sp(13));
        hit(again, A_NEW_SUDOKU, 0);
    }

    private void drawTetris(Canvas canvas) {
        drawBack(canvas, "Block Drop");
        if (tetris == null) {
            tetris = new TetrisEngine(random);
            tetrisLastFrame = SystemClock.elapsedRealtime();
        }

        float sidePanelWidth = dp(94);
        float controlsHeight = dp(164);
        float top = dp(62);
        float maxBoardHeight = getHeight() - top - controlsHeight - dp(12);
        float cell = Math.min((getWidth() - sidePanelWidth - dp(34)) / 10f,
                maxBoardHeight / 20f);
        float boardWidth = cell * 10;
        float boardHeight = cell * 20;
        float left = dp(12);
        float boardTop = top + Math.max(0, (maxBoardHeight - boardHeight) / 2);

        RectF boardRect = new RectF(left, boardTop, left + boardWidth,
                boardTop + boardHeight);
        roundRect(canvas, new RectF(boardRect.left - dp(3), boardRect.top - dp(3),
                boardRect.right + dp(3), boardRect.bottom + dp(3)), dp(5),
                palette.gridHeavy);
        canvas.drawRect(boardRect, fill(palette.tetrisBoard));

        for (int row = 0; row < TetrisEngine.ROWS; row++) {
            for (int col = 0; col < TetrisEngine.COLS; col++) {
                int value = tetris.board[row][col];
                if (value != 0) {
                    drawTetrisCell(canvas, left + col * cell, boardTop + row * cell,
                            cell, pieceColor(value - 1), false);
                }
            }
        }

        if (!tetris.gameOver) {
            int[] shape = tetris.activeShape();
            int ghostY = tetris.ghostY();
            for (int i = 0; i < 8; i += 2) {
                int col = tetris.x + shape[i];
                int ghostRow = ghostY + shape[i + 1];
                if (ghostRow >= 0) {
                    drawTetrisCell(canvas, left + col * cell,
                            boardTop + ghostRow * cell, cell, pieceColor(tetris.type), true);
                }
            }
            for (int i = 0; i < 8; i += 2) {
                int col = tetris.x + shape[i];
                int row = tetris.y + shape[i + 1];
                if (row >= 0) {
                    drawTetrisCell(canvas, left + col * cell, boardTop + row * cell,
                            cell, pieceColor(tetris.type), false);
                }
            }
        }

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(palette.tetrisGrid);
        strokePaint.setStrokeWidth(dp(0.45f));
        for (int i = 1; i < 10; i++) {
            canvas.drawLine(left + i * cell, boardTop, left + i * cell,
                    boardTop + boardHeight, strokePaint);
        }
        for (int i = 1; i < 20; i++) {
            canvas.drawLine(left, boardTop + i * cell, left + boardWidth,
                    boardTop + i * cell, strokePaint);
        }

        float panelLeft = boardRect.right + dp(12);
        drawTetrisPanel(canvas, panelLeft, boardTop, getWidth() - panelLeft - dp(10));
        drawTetrisControls(canvas, boardRect.bottom + dp(14));

        if (tetris.paused || tetris.gameOver) {
            drawTetrisOverlay(canvas, boardRect);
        }

        if (!hostPaused && !tetris.paused && !tetris.gameOver) {
            postInvalidateOnAnimation();
        }
    }

    private void drawTetrisPanel(Canvas canvas, float left, float top, float width) {
        text(canvas, "SCORE", left, top + dp(12), sp(10), palette.muted, true,
                Paint.Align.LEFT);
        text(canvas, tetris.formattedScore(), left, top + dp(33), sp(15), palette.text,
                true, Paint.Align.LEFT);
        text(canvas, "BEST", left, top + dp(64), sp(10), palette.muted, true,
                Paint.Align.LEFT);
        text(canvas, String.format(Locale.US, "%06d", tetrisHighScore), left,
                top + dp(85), sp(14), palette.text, true, Paint.Align.LEFT);
        text(canvas, "LEVEL", left, top + dp(116), sp(10), palette.muted, true,
                Paint.Align.LEFT);
        text(canvas, String.valueOf(tetris.level + 1), left, top + dp(139), sp(20),
                palette.orange, true, Paint.Align.LEFT);
        text(canvas, "LINES", left, top + dp(170), sp(10), palette.muted, true,
                Paint.Align.LEFT);
        text(canvas, String.valueOf(tetris.lines), left, top + dp(193), sp(20),
                palette.text, true, Paint.Align.LEFT);
        text(canvas, "NEXT", left, top + dp(226), sp(10), palette.muted, true,
                Paint.Align.LEFT);

        float previewCell = Math.min(dp(17), width / 4f);
        int[] preview = TetrisEngine.previewShape(tetris.nextType);
        for (int i = 0; i < 8; i += 2) {
            drawTetrisCell(canvas, left + preview[i] * previewCell,
                    top + dp(239) + preview[i + 1] * previewCell, previewCell,
                    pieceColor(tetris.nextType), false);
        }

        RectF pause = new RectF(left, top + dp(326), left + width, top + dp(368));
        if (pause.bottom < getHeight() - dp(170)) {
            pill(canvas, pause, tetris.paused ? "PLAY" : "PAUSE", palette.card,
                    palette.text, sp(10));
            hit(pause, A_T_PAUSE, 0);
        }
    }

    private void drawTetrisControls(Canvas canvas, float top) {
        float side = dp(12);
        float gap = dp(8);
        float rowHeight = dp(58);
        float third = (getWidth() - side * 2 - gap * 2) / 3;
        tetrisButton(canvas, new RectF(side, top, side + third, top + rowHeight),
                "←", "LEFT", A_T_LEFT);
        tetrisButton(canvas, new RectF(side + third + gap, top,
                        side + third * 2 + gap, top + rowHeight),
                "↻", "ROTATE", A_T_ROTATE);
        tetrisButton(canvas, new RectF(side + (third + gap) * 2, top,
                        getWidth() - side, top + rowHeight),
                "→", "RIGHT", A_T_RIGHT);

        float secondTop = top + rowHeight + gap;
        float half = (getWidth() - side * 2 - gap) / 2;
        tetrisButton(canvas, new RectF(side, secondTop, side + half,
                        secondTop + rowHeight), "↓", "SOFT DROP", A_T_SOFT);
        RectF hard = new RectF(side + half + gap, secondTop, getWidth() - side,
                secondTop + rowHeight);
        roundRect(canvas, hard, dp(14), palette.orange);
        text(canvas, "⇊", hard.centerX() - dp(32), hard.centerY() + dp(7), sp(21),
                Color.WHITE, true, Paint.Align.CENTER);
        text(canvas, "HARD DROP", hard.centerX() + dp(16), hard.centerY() + dp(5),
                sp(11), Color.WHITE, true, Paint.Align.CENTER);
        hit(hard, A_T_HARD, 0);
    }

    private void drawTetrisOverlay(Canvas canvas, RectF board) {
        canvas.drawRect(board, fill(withAlpha(palette.background, darkTheme ? 225 : 210)));
        String title = tetris.gameOver ? "GAME OVER" : "PAUSED";
        text(canvas, title, board.centerX(), board.centerY() - dp(26), sp(24),
                tetris.gameOver ? palette.red : palette.text, true, Paint.Align.CENTER);
        if (tetris.gameOver) {
            text(canvas, "Score " + tetris.formattedScore(), board.centerX(),
                    board.centerY() + dp(4), sp(13), palette.muted, false,
                    Paint.Align.CENTER);
        }
        RectF button = new RectF(board.left + dp(28), board.centerY() + dp(28),
                board.right - dp(28), board.centerY() + dp(80));
        pill(canvas, button, tetris.gameOver ? "PLAY AGAIN" : "RESUME",
                tetris.gameOver ? palette.orange : palette.blue, Color.WHITE, sp(12));
        hit(button, tetris.gameOver ? A_T_RESTART : A_T_PAUSE, 0);
    }

    private void drawTetrisCell(Canvas canvas, float x, float y, float size, int color,
                                boolean ghost) {
        float inset = Math.max(1, size * 0.07f);
        RectF rect = new RectF(x + inset, y + inset, x + size - inset, y + size - inset);
        if (ghost) {
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(Math.max(dp(1), size * 0.08f));
            strokePaint.setColor(withAlpha(color, 120));
            canvas.drawRoundRect(rect, size * 0.12f, size * 0.12f, strokePaint);
        } else {
            roundRect(canvas, rect, size * 0.12f, color);
            paint.setColor(withAlpha(Color.WHITE, 50));
            canvas.drawRoundRect(new RectF(rect.left + inset, rect.top + inset,
                            rect.right - inset, rect.top + size * 0.22f),
                    size * 0.06f, size * 0.06f, paint);
        }
    }

    private void updateSudokuClock() {
        long now = SystemClock.elapsedRealtime();
        if (sudokuLastTick == 0) {
            sudokuLastTick = now;
        }
        if (!hostPaused && sudoku != null && !sudoku.complete && !generating) {
            long delta = now - sudokuLastTick;
            if (delta >= 1000) {
                int seconds = (int) (delta / 1000);
                sudoku.elapsedSeconds += seconds;
                sudokuLastTick += seconds * 1000L;
                if (sudoku.elapsedSeconds % 10 == 0) {
                    saveSudoku();
                }
            }
            postInvalidateDelayed(250);
        } else {
            sudokuLastTick = now;
        }
    }

    private void updateTetris() {
        long now = SystemClock.elapsedRealtime();
        if (tetrisLastFrame == 0) {
            tetrisLastFrame = now;
        }
        long delta = Math.min(100, now - tetrisLastFrame);
        tetrisLastFrame = now;
        if (hostPaused || tetris == null || tetris.paused || tetris.gameOver) {
            return;
        }
        tetrisDropAccumulator += delta;
        long interval = tetris.dropIntervalMillis();
        while (tetrisDropAccumulator >= interval) {
            tetris.tick();
            tetrisDropAccumulator -= interval;
        }
        updateHighScore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            HitTarget target = hits.get(i);
            if (target.rect.contains(event.getX(), event.getY())) {
                dispatch(target.action, target.value);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                invalidate();
                return true;
            }
        }
        return true;
    }

    private void dispatch(int action, int value) {
        switch (action) {
            case A_THEME:
                darkTheme = !darkTheme;
                palette = new Palette(darkTheme);
                preferences.edit().putBoolean(KEY_DARK, darkTheme).apply();
                ((MainActivity) getContext()).applySystemTheme(darkTheme);
                break;
            case A_OPEN_SUDOKU:
                screen = LEVELS;
                break;
            case A_OPEN_TETRIS:
                screen = TETRIS;
                tetrisLastFrame = SystemClock.elapsedRealtime();
                if (tetris != null && !tetris.gameOver) {
                    tetris.paused = false;
                }
                break;
            case A_BACK:
                goBack();
                break;
            case A_DIFFICULTY:
                startSudoku(SudokuEngine.Difficulty.values()[value]);
                break;
            case A_CONTINUE_SUDOKU:
                screen = SUDOKU;
                selectedCell = findFirstEditable();
                sudokuLastTick = SystemClock.elapsedRealtime();
                break;
            case A_SUDOKU_CELL:
                if (!generating) {
                    selectedCell = value;
                }
                break;
            case A_NUMBER:
                if (sudoku != null && selectedCell >= 0) {
                    sudoku.enter(selectedCell, value, noteMode);
                    if (sudoku.isMistake(selectedCell)) {
                        showToast("That number is a mistake");
                    }
                    saveSudoku();
                }
                break;
            case A_ERASE:
                if (sudoku != null && selectedCell >= 0) {
                    sudoku.erase(selectedCell);
                    saveSudoku();
                }
                break;
            case A_NOTES:
                noteMode = !noteMode;
                showToast(noteMode ? "Notes on" : "Notes off");
                break;
            case A_HINT:
                if (sudoku != null) {
                    int cell = sudoku.revealHint(random);
                    if (cell >= 0) {
                        selectedCell = cell;
                        showToast(sudoku.hintsRemaining + " hints remaining");
                        saveSudoku();
                    } else if (sudoku.hintsRemaining == 0) {
                        showToast("No hints remaining");
                    }
                }
                break;
            case A_NEW_SUDOKU:
                if (sudoku != null) {
                    startSudoku(sudoku.difficulty);
                } else {
                    screen = LEVELS;
                }
                break;
            case A_T_LEFT:
                if (tetris != null) tetris.move(-1);
                break;
            case A_T_RIGHT:
                if (tetris != null) tetris.move(1);
                break;
            case A_T_ROTATE:
                if (tetris != null) tetris.rotate();
                break;
            case A_T_SOFT:
                if (tetris != null) tetris.softDrop();
                break;
            case A_T_HARD:
                if (tetris != null) tetris.hardDrop();
                updateHighScore();
                break;
            case A_T_PAUSE:
                if (tetris != null && !tetris.gameOver) {
                    tetris.paused = !tetris.paused;
                    tetrisLastFrame = SystemClock.elapsedRealtime();
                    tetrisDropAccumulator = 0;
                }
                break;
            case A_T_RESTART:
                if (tetris != null) {
                    updateHighScore();
                    tetris.reset();
                    tetrisLastFrame = SystemClock.elapsedRealtime();
                    tetrisDropAccumulator = 0;
                }
                break;
            default:
                break;
        }
    }

    private void startSudoku(final SudokuEngine.Difficulty difficulty) {
        screen = SUDOKU;
        generating = true;
        selectedCell = -1;
        noteMode = false;
        final int token = ++generationToken;
        invalidate();
        puzzleExecutor.execute(() -> {
            SudokuEngine.Puzzle puzzle = SudokuEngine.generate(difficulty, new Random());
            post(() -> {
                if (token != generationToken) {
                    return;
                }
                sudoku = new SudokuGame(difficulty, puzzle);
                generating = false;
                selectedCell = findFirstEditable();
                sudokuLastTick = SystemClock.elapsedRealtime();
                saveSudoku();
                invalidate();
            });
        });
    }

    private int findFirstEditable() {
        if (sudoku == null) {
            return -1;
        }
        for (int i = 0; i < 81; i++) {
            if (sudoku.isEditable(i)) {
                return i;
            }
        }
        return -1;
    }

    private void updateHighScore() {
        if (tetris != null && tetris.score > tetrisHighScore) {
            tetrisHighScore = tetris.score;
            preferences.edit().putInt(KEY_HIGH_SCORE, tetrisHighScore).apply();
        }
    }

    private void saveState() {
        saveSudoku();
        updateHighScore();
    }

    private void saveSudoku() {
        if (sudoku != null) {
            preferences.edit().putString(KEY_SUDOKU, sudoku.encode()).apply();
        }
    }

    private void showToast(String message) {
        toastMessage = message;
        toastUntil = SystemClock.elapsedRealtime() + 1700;
        postInvalidateOnAnimation();
    }

    private void drawToast(Canvas canvas) {
        if (toastMessage == null || SystemClock.elapsedRealtime() >= toastUntil) {
            toastMessage = null;
            return;
        }
        paint.setTextSize(sp(12));
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        float width = paint.measureText(toastMessage) + dp(28);
        RectF rect = new RectF((getWidth() - width) / 2, getHeight() - dp(54),
                (getWidth() + width) / 2, getHeight() - dp(18));
        roundRect(canvas, rect, dp(18), palette.toast);
        text(canvas, toastMessage, rect.centerX(), rect.centerY() + dp(4), sp(12),
                palette.toastText, true, Paint.Align.CENTER);
        postInvalidateDelayed(100);
    }

    private void drawBack(Canvas canvas, String title) {
        RectF back = new RectF(dp(8), dp(7), dp(50), dp(49));
        roundRect(canvas, back, dp(15), palette.card);
        text(canvas, "‹", back.centerX(), back.centerY() + dp(9), sp(31), palette.text,
                false, Paint.Align.CENTER);
        hit(back, A_BACK, 0);
        text(canvas, title, dp(62), dp(36), sp(20), palette.text, true, Paint.Align.LEFT);
    }

    private void stat(Canvas canvas, float left, float top, float width, String label,
                      String value) {
        text(canvas, label, left + width / 2, top, sp(9), palette.muted, true,
                Paint.Align.CENTER);
        text(canvas, value, left + width / 2, top + dp(23), sp(14), palette.text, true,
                Paint.Align.CENTER);
    }

    private void actionButton(Canvas canvas, RectF rect, String icon, String label,
                              boolean selected, int action) {
        int background = selected ? palette.blue : palette.card;
        int foreground = selected ? Color.WHITE : palette.text;
        roundRect(canvas, rect, dp(13), background);
        text(canvas, icon, rect.left + dp(24), rect.centerY() + dp(6), sp(18),
                foreground, true, Paint.Align.CENTER);
        text(canvas, label, rect.centerX() + dp(13), rect.centerY() + dp(4), sp(10),
                foreground, true, Paint.Align.CENTER);
        hit(rect, action, 0);
    }

    private void tetrisButton(Canvas canvas, RectF rect, String icon, String label, int action) {
        roundRect(canvas, rect, dp(14), palette.card);
        text(canvas, icon, rect.centerX() - dp(26), rect.centerY() + dp(7), sp(21),
                palette.text, true, Paint.Align.CENTER);
        text(canvas, label, rect.centerX() + dp(16), rect.centerY() + dp(4), sp(10),
                palette.muted, true, Paint.Align.CENTER);
        hit(rect, action, 0);
    }

    private void pill(Canvas canvas, RectF rect, String label, int background, int foreground,
                      float textSize) {
        roundRect(canvas, rect, rect.height() / 2, background);
        text(canvas, label, rect.centerX(), rect.centerY() + textSize * 0.34f, textSize,
                foreground, true, Paint.Align.CENTER);
    }

    private void shadowCard(Canvas canvas, RectF rect) {
        RectF shadow = new RectF(rect.left, rect.top + dp(2), rect.right,
                rect.bottom + dp(3));
        roundRect(canvas, shadow, dp(20), palette.shadow);
        roundRect(canvas, rect, dp(20), palette.card);
    }

    private void roundRect(Canvas canvas, RectF rect, float radius, int color) {
        canvas.drawRoundRect(rect, radius, radius, fill(color));
    }

    private Paint fill(int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        return paint;
    }

    private void text(Canvas canvas, String value, float x, float baseline, float size,
                      int color, boolean bold, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setTypeface(bold ? Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                : Typeface.DEFAULT);
        canvas.drawText(value, x, baseline, paint);
    }

    private void drawThemeIcon(Canvas canvas, float cx, float cy) {
        if (darkTheme) {
            fill(palette.orange);
            canvas.drawCircle(cx, cy, dp(8), paint);
            strokePaint.setColor(palette.orange);
            strokePaint.setStrokeWidth(dp(2));
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * i / 4;
                canvas.drawLine(cx + (float) Math.cos(angle) * dp(12),
                        cy + (float) Math.sin(angle) * dp(12),
                        cx + (float) Math.cos(angle) * dp(16),
                        cy + (float) Math.sin(angle) * dp(16), strokePaint);
            }
        } else {
            fill(palette.blue);
            canvas.drawCircle(cx, cy, dp(11), paint);
            fill(palette.card);
            canvas.drawCircle(cx + dp(6), cy - dp(5), dp(10), paint);
        }
    }

    private int difficultyAccent(int index) {
        if (index == 0) return palette.green;
        if (index == 1) return palette.blue;
        if (index == 2) return palette.orange;
        return palette.red;
    }

    private int pieceColor(int type) {
        int[] light = {
                Color.rgb(34, 211, 238),
                Color.rgb(250, 204, 21),
                Color.rgb(168, 85, 247),
                Color.rgb(59, 130, 246),
                Color.rgb(249, 115, 22),
                Color.rgb(34, 197, 94),
                Color.rgb(239, 68, 68)
        };
        return light[Math.max(0, Math.min(6, type))];
    }

    private void hit(RectF rect, int action, int value) {
        hits.add(new HitTarget(new RectF(rect), action, value));
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static final class HitTarget {
        final RectF rect;
        final int action;
        final int value;

        HitTarget(RectF rect, int action, int value) {
            this.rect = rect;
            this.action = action;
            this.value = value;
        }
    }

    private static final class Palette {
        final int background;
        final int card;
        final int board;
        final int text;
        final int muted;
        final int shadow;
        final int blue;
        final int orange;
        final int green;
        final int red;
        final int peer;
        final int same;
        final int selected;
        final int errorBackground;
        final int gridHeavy;
        final int gridLight;
        final int tetrisBoard;
        final int tetrisGrid;
        final int toast;
        final int toastText;

        Palette(boolean dark) {
            if (dark) {
                background = Color.rgb(11, 17, 32);
                card = Color.rgb(24, 34, 52);
                board = Color.rgb(17, 26, 43);
                text = Color.rgb(241, 245, 249);
                muted = Color.rgb(148, 163, 184);
                shadow = Color.argb(90, 0, 0, 0);
                peer = Color.rgb(27, 43, 68);
                same = Color.rgb(35, 61, 92);
                selected = Color.rgb(45, 85, 135);
                errorBackground = Color.rgb(73, 28, 37);
                gridHeavy = Color.rgb(100, 116, 139);
                gridLight = Color.rgb(48, 61, 79);
                tetrisBoard = Color.rgb(7, 12, 24);
                tetrisGrid = Color.rgb(25, 38, 58);
                toast = Color.rgb(226, 232, 240);
                toastText = Color.rgb(15, 23, 42);
            } else {
                background = Color.rgb(248, 250, 252);
                card = Color.WHITE;
                board = Color.WHITE;
                text = Color.rgb(15, 23, 42);
                muted = Color.rgb(100, 116, 139);
                shadow = Color.argb(18, 15, 23, 42);
                peer = Color.rgb(239, 246, 255);
                same = Color.rgb(219, 234, 254);
                selected = Color.rgb(191, 219, 254);
                errorBackground = Color.rgb(254, 226, 226);
                gridHeavy = Color.rgb(51, 65, 85);
                gridLight = Color.rgb(203, 213, 225);
                tetrisBoard = Color.rgb(241, 245, 249);
                tetrisGrid = Color.rgb(226, 232, 240);
                toast = Color.rgb(15, 23, 42);
                toastText = Color.WHITE;
            }
            blue = Color.rgb(37, 99, 235);
            orange = Color.rgb(234, 88, 12);
            green = Color.rgb(22, 163, 74);
            red = Color.rgb(220, 38, 38);
        }
    }
}
