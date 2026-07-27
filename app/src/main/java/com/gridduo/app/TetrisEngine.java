package com.gridduo.app;

import java.util.Locale;
import java.util.Random;

final class TetrisEngine {
    static final int ROWS = 20;
    static final int COLS = 10;

    // Piece order: I, O, T, J, L, S, Z. Each rotation stores four x/y pairs.
    private static final int[][][] SHAPES = {
            {
                    {0, 1, 1, 1, 2, 1, 3, 1},
                    {2, 0, 2, 1, 2, 2, 2, 3},
                    {0, 2, 1, 2, 2, 2, 3, 2},
                    {1, 0, 1, 1, 1, 2, 1, 3}
            },
            {
                    {1, 0, 2, 0, 1, 1, 2, 1},
                    {1, 0, 2, 0, 1, 1, 2, 1},
                    {1, 0, 2, 0, 1, 1, 2, 1},
                    {1, 0, 2, 0, 1, 1, 2, 1}
            },
            {
                    {1, 0, 0, 1, 1, 1, 2, 1},
                    {1, 0, 1, 1, 2, 1, 1, 2},
                    {0, 1, 1, 1, 2, 1, 1, 2},
                    {1, 0, 0, 1, 1, 1, 1, 2}
            },
            {
                    {0, 0, 0, 1, 1, 1, 2, 1},
                    {1, 0, 2, 0, 1, 1, 1, 2},
                    {0, 1, 1, 1, 2, 1, 2, 2},
                    {1, 0, 1, 1, 0, 2, 1, 2}
            },
            {
                    {2, 0, 0, 1, 1, 1, 2, 1},
                    {1, 0, 1, 1, 1, 2, 2, 2},
                    {0, 1, 1, 1, 2, 1, 0, 2},
                    {0, 0, 1, 0, 1, 1, 1, 2}
            },
            {
                    {1, 0, 2, 0, 0, 1, 1, 1},
                    {1, 0, 1, 1, 2, 1, 2, 2},
                    {1, 1, 2, 1, 0, 2, 1, 2},
                    {0, 0, 0, 1, 1, 1, 1, 2}
            },
            {
                    {0, 0, 1, 0, 1, 1, 2, 1},
                    {2, 0, 1, 1, 2, 1, 1, 2},
                    {0, 1, 1, 1, 1, 2, 2, 2},
                    {1, 0, 0, 1, 1, 1, 0, 2}
            }
    };

    final int[][] board = new int[ROWS][COLS];
    private final Random random;
    private final int[] bag = new int[7];
    private int bagIndex = 7;

    int type;
    int rotation;
    int x;
    int y;
    int nextType;
    int score;
    int lines;
    int level;
    boolean gameOver;
    boolean paused;
    boolean started;

    TetrisEngine(Random random) {
        this.random = random;
        reset();
    }

    void reset() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                board[r][c] = 0;
            }
        }
        bagIndex = 7;
        score = 0;
        lines = 0;
        level = 0;
        gameOver = false;
        paused = false;
        started = true;
        nextType = takeFromBag();
        spawn();
    }

    private void spawn() {
        type = nextType;
        nextType = takeFromBag();
        rotation = 0;
        x = 3;
        y = -1;
        if (!isValid(type, rotation, x, y)) {
            gameOver = true;
        }
    }

    private int takeFromBag() {
        if (bagIndex >= 7) {
            for (int i = 0; i < 7; i++) {
                bag[i] = i;
            }
            for (int i = 6; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int swap = bag[i];
                bag[i] = bag[j];
                bag[j] = swap;
            }
            bagIndex = 0;
        }
        return bag[bagIndex++];
    }

    boolean move(int dx) {
        if (!canControl()) {
            return false;
        }
        if (isValid(type, rotation, x + dx, y)) {
            x += dx;
            return true;
        }
        return false;
    }

    boolean rotate() {
        if (!canControl()) {
            return false;
        }
        int nextRotation = (rotation + 1) & 3;
        int[] kicks = {0, -1, 1, -2, 2};
        for (int kick : kicks) {
            if (isValid(type, nextRotation, x + kick, y)) {
                x += kick;
                rotation = nextRotation;
                return true;
            }
        }
        if (isValid(type, nextRotation, x, y - 1)) {
            y--;
            rotation = nextRotation;
            return true;
        }
        return false;
    }

    boolean softDrop() {
        if (!canControl()) {
            return false;
        }
        if (isValid(type, rotation, x, y + 1)) {
            y++;
            score++;
            return true;
        }
        lockPiece();
        return false;
    }

    void tick() {
        if (!canControl()) {
            return;
        }
        if (isValid(type, rotation, x, y + 1)) {
            y++;
        } else {
            lockPiece();
        }
    }

    void hardDrop() {
        if (!canControl()) {
            return;
        }
        int distance = 0;
        while (isValid(type, rotation, x, y + 1)) {
            y++;
            distance++;
        }
        score += distance * 2;
        lockPiece();
    }

    int ghostY() {
        int result = y;
        while (isValid(type, rotation, x, result + 1)) {
            result++;
        }
        return result;
    }

    private void lockPiece() {
        int[] shape = SHAPES[type][rotation];
        for (int i = 0; i < 8; i += 2) {
            int col = x + shape[i];
            int row = y + shape[i + 1];
            if (row < 0) {
                gameOver = true;
                return;
            }
            board[row][col] = type + 1;
        }
        int cleared = clearLines();
        if (cleared > 0) {
            int[] awards = {0, 100, 300, 500, 800};
            score += awards[cleared] * (level + 1);
            lines += cleared;
            level = lines / 10;
        }
        spawn();
    }

    private int clearLines() {
        int cleared = 0;
        for (int row = ROWS - 1; row >= 0; row--) {
            boolean full = true;
            for (int col = 0; col < COLS; col++) {
                if (board[row][col] == 0) {
                    full = false;
                    break;
                }
            }
            if (!full) {
                continue;
            }
            cleared++;
            for (int pull = row; pull > 0; pull--) {
                System.arraycopy(board[pull - 1], 0, board[pull], 0, COLS);
            }
            for (int col = 0; col < COLS; col++) {
                board[0][col] = 0;
            }
            row++;
        }
        return cleared;
    }

    private boolean isValid(int piece, int pieceRotation, int pieceX, int pieceY) {
        int[] shape = SHAPES[piece][pieceRotation];
        for (int i = 0; i < 8; i += 2) {
            int col = pieceX + shape[i];
            int row = pieceY + shape[i + 1];
            if (col < 0 || col >= COLS || row >= ROWS) {
                return false;
            }
            if (row >= 0 && board[row][col] != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean canControl() {
        return started && !gameOver && !paused;
    }

    int[] activeShape() {
        return SHAPES[type][rotation];
    }

    static int[] previewShape(int piece) {
        return SHAPES[piece][0];
    }

    long dropIntervalMillis() {
        return Math.max(70L, (long) (800.0 * Math.pow(0.84, level)));
    }

    String formattedScore() {
        return String.format(Locale.US, "%06d", score);
    }
}

