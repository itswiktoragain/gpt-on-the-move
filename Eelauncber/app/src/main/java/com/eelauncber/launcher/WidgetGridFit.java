package com.eelauncber.launcher;

/** Pure grid geometry. A finger may be over any cell covered by a large widget. */
final class WidgetGridFit {
    static final int COLS = 4, ROWS = 5, CELLS = COLS * ROWS;

    private WidgetGridFit() {}

    static int hoveredCell(float x, float y, int width, int height) {
        int column = clamp((int) (x * COLS / Math.max(1, width)), 0, COLS - 1);
        int row = clamp((int) (y * ROWS / Math.max(1, height)), 0, ROWS - 1);
        return row * COLS + column;
    }

    /**
     * Return the nearest unoccupied legal TOP-LEFT cell for the entire widget
     * rectangle, not the cell below the finger. This is vital for a 4-wide or
     * 5-tall widget, whose origin is restricted to column 0 or row 0.
     */
    static int nearest(boolean[] occupied, int hovered, int spanX, int spanY) {
        if (occupied == null || occupied.length < CELLS ||
                hovered < 0 || hovered >= CELLS ||
                spanX < 1 || spanX > COLS || spanY < 1 || spanY > ROWS)
            return -1;

        // The pointer grabs the widget close to its center rather than its
        // top-left cell; clamp that requested origin to the grid boundaries.
        int targetColumn = clamp(hovered % COLS - (spanX - 1) / 2,
                0, COLS - spanX);
        int targetRow = clamp(hovered / COLS - (spanY - 1) / 2,
                0, ROWS - spanY);

        int result = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int row = 0; row <= ROWS - spanY; row++) {
            for (int column = 0; column <= COLS - spanX; column++) {
                int origin = row * COLS + column;
                if (!fits(occupied, origin, spanX, spanY)) continue;
                int dx = column - targetColumn;
                int dy = row - targetRow;
                int distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    result = origin;
                }
            }
        }
        return result;
    }

    static boolean fits(boolean[] occupied, int cell, int width, int height) {
        if (occupied == null || occupied.length < CELLS || cell < 0 || cell >= CELLS ||
                width < 1 || height < 1 || cell % COLS + width > COLS ||
                cell / COLS + height > ROWS)
            return false;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (occupied[cell + y * COLS + x]) return false;
        return true;
    }

    static void occupy(boolean[] cells, int cell, int width, int height) {
        if (cell < 0 || cell >= CELLS || width < 1 || height < 1 ||
                cell % COLS + width > COLS || cell / COLS + height > ROWS) return;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                cells[cell + y * COLS + x] = true;
    }

    private static int clamp(int n, int min, int max) {
        return Math.max(min, Math.min(max, n));
    }
}
