/*
 This file is part of Privacy Friendly Sudoku.

 Privacy Friendly Sudoku is free software:
 you can redistribute it and/or modify it under the terms of the
 GNU General Public License as published by the Free Software Foundation,
 either version 3 of the License, or any later version.

 Privacy Friendly Sudoku is distributed in the hope
 that it will be useful, but WITHOUT ANY WARRANTY; without even
 the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Privacy Friendly Sudoku. If not, see <http://www.gnu.org/licenses/>.
 */
package org.secuso.privacyfriendlysudoku.controller.qqwing;

import java.util.LinkedList;

public enum GameType {
    Unspecified(1,1,1),
    Default_9x9(9,3,3),
    Default_12x12(12,3,4),
    Default_6x6(6,2,3),
    X_9x9(9,3,3),
    Hyper_9x9(9,3,3);

    // change pictures for unsepc x9x9 and hyper 9x9 as soon as available
    int sectionWidth;
    int sectionHeight;
    int size;

    GameType(int size, int sectionHeight, int sectionWidth) {
        this.size = size;
        this.sectionHeight = sectionHeight;
        this.sectionWidth = sectionWidth;
    }

    public static LinkedList<GameType> getValidGameTypes() {
        LinkedList<GameType> result = new LinkedList<>();
        result.add(Default_6x6);
        result.add(Default_9x9);
        result.add(Default_12x12);
        return result;
    }

    public int getSectionHeight() {
        return sectionHeight;
    }
    public int getSize() {
        return size;
    }

    public int getSectionWidth() {
        return sectionWidth;
    }

    public static GameType of(int boardSize) {
        return switch (boardSize) {
            case 6 * 6 -> Default_6x6;
            case 9 * 9 -> Default_9x9;
            case 12 * 12 -> Default_12x12;
            default -> throw new IllegalArgumentException("Unsupported board size: " + boardSize);
        };
    }
}
