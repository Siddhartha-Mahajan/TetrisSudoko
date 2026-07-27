package com.gridduo.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

final class SudokuGame {
    static final int MAX_MISTAKES = 3;

    final SudokuEngine.Difficulty difficulty;
    final int[] puzzle;
    final int[] solution;
    final int[] values = new int[81];
    final int[] notes = new int[81];
    final boolean[] hinted = new boolean[81];
    int hintsRemaining = 3;
    int mistakes;
    int elapsedSeconds;
    boolean complete;

    SudokuGame(SudokuEngine.Difficulty difficulty, SudokuEngine.Puzzle generated) {
        this.difficulty = difficulty;
        puzzle = generated.clues.clone();
        solution = generated.solution.clone();
        System.arraycopy(puzzle, 0, values, 0, 81);
    }

    boolean isEditable(int cell) {
        return cell >= 0 && cell < 81 && puzzle[cell] == 0 && !hinted[cell]
                && !isOver();
    }

    boolean isMistake(int cell) {
        return values[cell] != 0 && values[cell] != solution[cell];
    }

    boolean hasFailed() {
        return mistakes >= MAX_MISTAKES;
    }

    boolean isOver() {
        return complete || hasFailed();
    }

    void enter(int cell, int number, boolean noteMode) {
        if (!isEditable(cell) || number < 1 || number > 9) {
            return;
        }
        if (noteMode) {
            if (values[cell] == 0) {
                notes[cell] ^= 1 << (number - 1);
            }
            return;
        }

        values[cell] = number;
        notes[cell] = 0;
        if (number != solution[cell]) {
            mistakes++;
        } else {
            clearPeerNotes(cell, number);
            checkComplete();
        }
    }

    void erase(int cell) {
        if (!isEditable(cell)) {
            return;
        }
        values[cell] = 0;
        notes[cell] = 0;
    }

    int revealHint(Random random) {
        if (hintsRemaining <= 0 || isOver()) {
            return -1;
        }
        List<Integer> candidates = new ArrayList<>();
        for (int cell = 0; cell < 81; cell++) {
            if (puzzle[cell] == 0 && !hinted[cell] && values[cell] != solution[cell]) {
                candidates.add(cell);
            }
        }
        if (candidates.isEmpty()) {
            checkComplete();
            return -1;
        }
        int cell = candidates.get(random.nextInt(candidates.size()));
        values[cell] = solution[cell];
        notes[cell] = 0;
        hinted[cell] = true;
        hintsRemaining--;
        clearPeerNotes(cell, solution[cell]);
        checkComplete();
        return cell;
    }

    private void clearPeerNotes(int cell, int number) {
        int mask = ~(1 << (number - 1));
        int row = cell / 9;
        int col = cell % 9;
        for (int i = 0; i < 9; i++) {
            notes[row * 9 + i] &= mask;
            notes[i * 9 + col] &= mask;
        }
        int boxRow = (row / 3) * 3;
        int boxCol = (col / 3) * 3;
        for (int r = boxRow; r < boxRow + 3; r++) {
            for (int c = boxCol; c < boxCol + 3; c++) {
                notes[r * 9 + c] &= mask;
            }
        }
    }

    private void checkComplete() {
        for (int cell = 0; cell < 81; cell++) {
            if (values[cell] != solution[cell]) {
                complete = false;
                return;
            }
        }
        complete = true;
    }

    String encode() {
        return "1|" + difficulty.ordinal()
                + "|" + hintsRemaining
                + "|" + mistakes
                + "|" + elapsedSeconds
                + "|" + digits(puzzle)
                + "|" + digits(solution)
                + "|" + digits(values)
                + "|" + noteString()
                + "|" + booleans(hinted)
                + "|" + (complete ? "1" : "0");
    }

    static SudokuGame decode(String encoded) {
        try {
            String[] parts = encoded.split("\\|", -1);
            if (parts.length != 11 || !"1".equals(parts[0])) {
                return null;
            }
            SudokuEngine.Difficulty difficulty =
                    SudokuEngine.Difficulty.values()[Integer.parseInt(parts[1])];
            int[] puzzle = parseDigits(parts[5]);
            int[] solution = parseDigits(parts[6]);
            int[] savedValues = parseDigits(parts[7]);
            if (puzzle == null || solution == null || savedValues == null) {
                return null;
            }
            SudokuGame game = new SudokuGame(
                    difficulty, new SudokuEngine.Puzzle(puzzle, solution));
            game.hintsRemaining = Math.max(0, Math.min(3, Integer.parseInt(parts[2])));
            game.mistakes = Math.max(0,
                    Math.min(MAX_MISTAKES, Integer.parseInt(parts[3])));
            game.elapsedSeconds = Math.max(0, Integer.parseInt(parts[4]));
            System.arraycopy(savedValues, 0, game.values, 0, 81);
            String[] savedNotes = parts[8].split("\\.", -1);
            if (savedNotes.length != 81 || parts[9].length() != 81) {
                return null;
            }
            for (int i = 0; i < 81; i++) {
                game.notes[i] = Integer.parseInt(savedNotes[i], 36) & 0x1ff;
                game.hinted[i] = parts[9].charAt(i) == '1';
            }
            game.complete = "1".equals(parts[10]);
            return game;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String digits(int[] values) {
        StringBuilder builder = new StringBuilder(81);
        for (int value : values) {
            builder.append((char) ('0' + value));
        }
        return builder.toString();
    }

    private static int[] parseDigits(String value) {
        if (value.length() != 81) {
            return null;
        }
        int[] parsed = new int[81];
        for (int i = 0; i < 81; i++) {
            char digit = value.charAt(i);
            if (digit < '0' || digit > '9') {
                return null;
            }
            parsed[i] = digit - '0';
        }
        return parsed;
    }

    private String noteString() {
        StringBuilder builder = new StringBuilder(250);
        for (int i = 0; i < 81; i++) {
            if (i > 0) {
                builder.append('.');
            }
            builder.append(Integer.toString(notes[i], 36));
        }
        return builder.toString();
    }

    private static String booleans(boolean[] values) {
        StringBuilder builder = new StringBuilder(values.length);
        for (boolean value : values) {
            builder.append(value ? '1' : '0');
        }
        return builder.toString();
    }

    String formattedTime() {
        return String.format(Locale.US, "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60);
    }
}
