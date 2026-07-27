package com.gridduo.app;

import java.util.Arrays;
import java.util.Random;

/**
 * Dependency-free engine checks. Run with tools/run-engine-tests.sh.
 */
public final class EngineSelfTest {
    private EngineSelfTest() {
    }

    public static void main(String[] args) {
        testSudokuGeneration();
        testSudokuGameActionsAndPersistence();
        testTetrisMovementAndLineClear();
        System.out.println("All engine self-tests passed.");
    }

    private static void testSudokuGeneration() {
        Random random = new Random(1739);
        for (SudokuEngine.Difficulty difficulty : SudokuEngine.Difficulty.values()) {
            for (int iteration = 0; iteration < 2; iteration++) {
                long started = System.nanoTime();
                SudokuEngine.Puzzle puzzle = SudokuEngine.generate(difficulty, random);
                require(puzzle != null, "Puzzle was null");
                require(validSolution(puzzle.solution), "Generated solution is invalid");
                require(SudokuEngine.countSolutions(puzzle.clues, 2) == 1,
                        difficulty + " puzzle is not unique");
                for (int i = 0; i < 81; i++) {
                    if (puzzle.clues[i] != 0) {
                        require(puzzle.clues[i] == puzzle.solution[i],
                                "Clue differs from solution");
                    }
                }
                require(SudokuEngine.rate(puzzle.clues) == difficulty,
                        difficulty + " puzzle has the wrong logical rating");
                long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
                require(elapsedMs < 30_000,
                        difficulty + " generation took too long: " + elapsedMs + " ms");
            }
        }
    }

    private static boolean validSolution(int[] solution) {
        if (solution == null || solution.length != 81) {
            return false;
        }
        for (int row = 0; row < 9; row++) {
            boolean[] seen = new boolean[10];
            for (int col = 0; col < 9; col++) {
                int value = solution[row * 9 + col];
                if (value < 1 || value > 9 || seen[value]) {
                    return false;
                }
                seen[value] = true;
            }
        }
        for (int col = 0; col < 9; col++) {
            boolean[] seen = new boolean[10];
            for (int row = 0; row < 9; row++) {
                int value = solution[row * 9 + col];
                if (seen[value]) {
                    return false;
                }
                seen[value] = true;
            }
        }
        return true;
    }

    private static void testSudokuGameActionsAndPersistence() {
        SudokuEngine.Puzzle generated = SudokuEngine.generate(
                SudokuEngine.Difficulty.MEDIUM, new Random(42));
        SudokuGame game = new SudokuGame(SudokuEngine.Difficulty.MEDIUM, generated);
        int editable = firstEmpty(game.puzzle);
        int wrong = game.solution[editable] == 9 ? 8 : 9;
        game.enter(editable, wrong, false);
        require(game.isMistake(editable), "Wrong value was not marked");
        require(game.mistakes == 1, "Mistake counter did not increase");
        game.erase(editable);
        require(game.values[editable] == 0, "Erase failed");
        game.enter(editable, 3, true);
        require((game.notes[editable] & (1 << 2)) != 0, "Note was not added");
        game.enter(editable, 3, true);
        require((game.notes[editable] & (1 << 2)) == 0, "Note was not toggled off");
        int hintedCell = game.revealHint(new Random(7));
        require(hintedCell >= 0, "Hint did not reveal a cell");
        require(game.hintsRemaining == 2, "Hint allowance is incorrect");
        require(game.values[hintedCell] == game.solution[hintedCell], "Hint value is wrong");

        game.elapsedSeconds = 137;
        SudokuGame restored = SudokuGame.decode(game.encode());
        require(restored != null, "Saved Sudoku could not be decoded");
        require(Arrays.equals(game.values, restored.values), "Saved values changed");
        require(Arrays.equals(game.notes, restored.notes), "Saved notes changed");
        require(restored.elapsedSeconds == 137, "Saved timer changed");
        require(restored.hintsRemaining == 2, "Saved hints changed");
    }

    private static int firstEmpty(int[] values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == 0) {
                return i;
            }
        }
        throw new AssertionError("Puzzle has no empty cell");
    }

    private static void testTetrisMovementAndLineClear() {
        TetrisEngine engine = new TetrisEngine(new Random(3));
        for (int col = 0; col < 6; col++) {
            engine.board[19][col] = 1;
        }
        engine.type = 0;
        engine.rotation = 0;
        engine.x = 6;
        engine.y = 17;
        engine.hardDrop();
        require(engine.lines == 1, "Completed Tetris line was not cleared");
        require(engine.score >= 100, "Line-clear score was not awarded");
        for (int col = 0; col < TetrisEngine.COLS; col++) {
            require(engine.board[19][col] == 0, "Cleared row still contains blocks");
        }

        engine.reset();
        for (int i = 0; i < 30 && !engine.gameOver; i++) {
            engine.rotate();
            engine.move(i % 2 == 0 ? -1 : 1);
            engine.hardDrop();
        }
        require(engine.score > 0, "Hard drops did not score");
        require(engine.dropIntervalMillis() >= 70, "Drop interval fell below its limit");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
