package com.romanysrael.battleofbluffs.game.domain;

public record Position(int row, int column) {
    public static final int ROW_COUNT = 8;
    public static final int COLUMN_COUNT = 9;

    public Position {
        if (row < 0 || row >= ROW_COUNT || column < 0 || column >= COLUMN_COUNT) {
            throw new IllegalArgumentException("Position is outside the 8x9 board: (" + row + ", " + column + ")");
        }
    }

    public int manhattanDistanceTo(Position other) {
        return Math.abs(row - other.row) + Math.abs(column - other.column);
    }

    public boolean isOrthogonallyAdjacentTo(Position other) {
        return manhattanDistanceTo(other) == 1;
    }
}
