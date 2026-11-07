package sa.urtv.home.tilawah;

/**
 * The surah grid reads from the right (cell 0 is on the right), and a left/right key is walked by hand so «right» always goes right.
 * At the grid's two ends there is no cell to go to; the key must then stay put and never reach Android's own search, which counts
 * cells the other way round. No Android in here: tests/CellWalkRun.java runs it.
 */
final class CellWalk {
    private CellWalk() {}

    static final int NONE = -1;

    /** The cell on the right (true) or on the left of {@code position}, or NONE at an end of the grid. */
    static int next(int position, int count, boolean right) {
        int n = right ? position - 1 : position + 1;
        return n < 0 || n >= count ? NONE : n;
    }

    /** Whether the key is consumed here: it moved, it is a repeat of a held key, or the focus is on a cell and has nowhere to go (an end). */
    static boolean consume(boolean moved, int repeat, boolean onCell) {
        return moved || repeat > 0 || onCell;
    }
}
