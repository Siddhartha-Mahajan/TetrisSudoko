package com.gridduo.app;

import java.util.Random;

import org.secuso.privacyfriendlysudoku.controller.qqwing.GameDifficulty;
import org.secuso.privacyfriendlysudoku.controller.qqwing.GameType;
import org.secuso.privacyfriendlysudoku.controller.qqwing.QQWing;

final class SudokuEngine {
    enum Difficulty {
        EASY("Easy", "Singles and straightforward logic", GameDifficulty.Easy),
        MEDIUM("Medium", "Hidden singles and pairs", GameDifficulty.Moderate),
        HARD("Hard", "Box and line interactions", GameDifficulty.Hard),
        EXPERT("Expert", "Requires careful branching", GameDifficulty.Challenge);

        final String label;
        final String description;
        final GameDifficulty qqWingDifficulty;

        Difficulty(String label, String description, GameDifficulty qqWingDifficulty) {
            this.label = label;
            this.description = description;
            this.qqWingDifficulty = qqWingDifficulty;
        }
    }

    static final class Puzzle {
        final int[] clues;
        final int[] solution;

        Puzzle(int[] clues, int[] solution) {
            this.clues = clues;
            this.solution = solution;
        }
    }

    private SudokuEngine() {
    }

    static Puzzle generate(Difficulty difficulty, Random random) {
        QQWing generator = new QQWing(
                GameType.Default_9x9, difficulty.qqWingDifficulty);
        generator.setRandom(random.nextInt());
        generator.setRecordHistory(true);

        // This is the same generate → solve → technique-rate loop used by
        // LibreSudoku's QQWingController. QQWing removes every clue it can while
        // preserving one solution; puzzles are accepted only when the solving
        // techniques match the level the player selected.
        while (!Thread.currentThread().isInterrupted()) {
            generator.generatePuzzle();
            if (generator.countSolutionsLimited() != 1) {
                continue;
            }
            generator.solve();
            if (generator.getDifficulty() == difficulty.qqWingDifficulty) {
                return new Puzzle(generator.getPuzzle(), generator.getSolution());
            }
        }
        throw new IllegalStateException("Sudoku generation was interrupted");
    }

    static int countSolutions(int[] source, int limit) {
        return countSolutionsInPlace(source.clone(), limit);
    }

    static Difficulty rate(int[] puzzle) {
        QQWing solver = new QQWing(GameType.Default_9x9, GameDifficulty.Unspecified);
        solver.setRecordHistory(true);
        if (!solver.setPuzzle(puzzle) || !solver.solve()) {
            return null;
        }
        GameDifficulty rating = solver.getDifficulty();
        for (Difficulty difficulty : Difficulty.values()) {
            if (difficulty.qqWingDifficulty == rating) {
                return difficulty;
            }
        }
        return null;
    }

    private static int countSolutionsInPlace(int[] board, int limit) {
        int chosen = -1;
        int chosenMask = 0;
        int fewest = 10;

        for (int cell = 0; cell < 81; cell++) {
            if (board[cell] != 0) {
                continue;
            }
            int mask = candidateMask(board, cell);
            int count = Integer.bitCount(mask);
            if (count == 0) {
                return 0;
            }
            if (count < fewest) {
                fewest = count;
                chosen = cell;
                chosenMask = mask;
                if (count == 1) {
                    break;
                }
            }
        }

        if (chosen == -1) {
            return 1;
        }

        int solutions = 0;
        while (chosenMask != 0 && solutions < limit) {
            int bit = chosenMask & -chosenMask;
            chosenMask -= bit;
            board[chosen] = Integer.numberOfTrailingZeros(bit) + 1;
            solutions += countSolutionsInPlace(board, limit - solutions);
        }
        board[chosen] = 0;
        return solutions;
    }

    private static int candidateMask(int[] board, int cell) {
        int row = cell / 9;
        int col = cell % 9;
        int used = 0;
        for (int i = 0; i < 9; i++) {
            int rowValue = board[row * 9 + i];
            int colValue = board[i * 9 + col];
            if (rowValue != 0) {
                used |= 1 << (rowValue - 1);
            }
            if (colValue != 0) {
                used |= 1 << (colValue - 1);
            }
        }
        int boxRow = (row / 3) * 3;
        int boxCol = (col / 3) * 3;
        for (int r = boxRow; r < boxRow + 3; r++) {
            for (int c = boxCol; c < boxCol + 3; c++) {
                int value = board[r * 9 + c];
                if (value != 0) {
                    used |= 1 << (value - 1);
                }
            }
        }
        return 0x1ff & ~used;
    }
}
