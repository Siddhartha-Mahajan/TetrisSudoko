package com.gridduo.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class SudokuEngine {
    enum Difficulty {
        EASY("Easy", 42, "A relaxed start"),
        MEDIUM("Medium", 35, "A balanced challenge"),
        HARD("Hard", 29, "Fewer clues, deeper logic"),
        EXPERT("Expert", 25, "For seasoned solvers");

        final String label;
        final int clueCount;
        final String description;

        Difficulty(String label, int clueCount, String description) {
            this.label = label;
            this.clueCount = clueCount;
            this.description = description;
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
        int[] bestPuzzle = null;
        int[] bestSolution = null;
        int bestClues = 82;

        // A removal pass can occasionally reach a locally minimal puzzle before the
        // requested clue count. Fresh solved grids make the requested count reliable.
        for (int attempt = 0; attempt < 8; attempt++) {
            int[] solution = randomizedSolution(random);
            int[] puzzle = solution.clone();
            List<Integer> cells = new ArrayList<>(81);
            for (int i = 0; i < 81; i++) {
                cells.add(i);
            }
            Collections.shuffle(cells, random);

            int clues = 81;
            for (int cell : cells) {
                if (clues <= difficulty.clueCount) {
                    break;
                }
                int saved = puzzle[cell];
                puzzle[cell] = 0;
                if (countSolutions(puzzle, 2) != 1) {
                    puzzle[cell] = saved;
                } else {
                    clues--;
                }
            }

            if (clues < bestClues) {
                bestClues = clues;
                bestPuzzle = puzzle;
                bestSolution = solution;
            }
            if (clues == difficulty.clueCount) {
                return new Puzzle(puzzle, solution);
            }
        }
        return new Puzzle(bestPuzzle, bestSolution);
    }

    private static int[] randomizedSolution(Random random) {
        List<Integer> rowGroups = shuffled012(random);
        List<Integer> columnGroups = shuffled012(random);
        List<Integer> rows = new ArrayList<>(9);
        List<Integer> columns = new ArrayList<>(9);

        for (int group : rowGroups) {
            List<Integer> inside = shuffled012(random);
            for (int value : inside) {
                rows.add(group * 3 + value);
            }
        }
        for (int group : columnGroups) {
            List<Integer> inside = shuffled012(random);
            for (int value : inside) {
                columns.add(group * 3 + value);
            }
        }

        List<Integer> digits = new ArrayList<>(9);
        for (int value = 1; value <= 9; value++) {
            digits.add(value);
        }
        Collections.shuffle(digits, random);

        int[] solution = new int[81];
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                int patternValue = (rows.get(r) * 3 + rows.get(r) / 3 + columns.get(c)) % 9;
                solution[r * 9 + c] = digits.get(patternValue);
            }
        }
        return solution;
    }

    private static List<Integer> shuffled012(Random random) {
        List<Integer> values = new ArrayList<>(3);
        values.add(0);
        values.add(1);
        values.add(2);
        Collections.shuffle(values, random);
        return values;
    }

    static int countSolutions(int[] source, int limit) {
        return countSolutionsInPlace(source.clone(), limit);
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

