package sa.urtv.home.tilawah;

/**
 * The Player's left/right seek while the key is held: the remote repeats the key every 50 ms or so, and each repeat used to send
 * the service another seek. Now one seek goes through per 250 ms; a fresh press (no repeat) always does. Plain Java:
 * tests/PlayerLogicRun.java runs it.
 */
final class SeekPace {
    static final long GAP_MS = 250;
    private long last = Long.MIN_VALUE / 2;

    /** Should this key event seek? repeat: the key's repeat count (0 = a fresh press); now: the event's time in ms. */
    boolean due(int repeat, long now) {
        if (repeat > 0 && now - last < GAP_MS) return false;
        last = now;
        return true;
    }
}
