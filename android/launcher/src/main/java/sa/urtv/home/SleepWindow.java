package sa.urtv.home;

/**
 * Whether a saved sleep timer is still a timer, with no Android in it (tests/SleepWindowRun.java runs it). The alarm does not survive a
 * restart of the TV, so a timer saved before one must not be shown as running. The wall clock cannot tell (it goes on), but the time
 * since boot starts again from nothing: a timer set at {startElapsed} can only be this boot's if the TV has been up at least that long.
 */
final class SleepWindow {
    private SleepWindow() { }

    /** true when the timer ends in the future and was set in this boot (a restart may leave nothing to compare with: then only the clock decides) */
    static boolean running(long endWall, long nowWall, long startElapsed, long nowElapsed) {
        if (endWall <= nowWall) return false;
        return startElapsed <= 0 || nowElapsed >= startElapsed;
    }
}
