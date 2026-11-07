package sa.urtv.home;

/** For tests/sleep_boot_test.py: SleepWindow.java. Prints one line per check: name TAB ok. */
public final class SleepWindowRun {
    static void check(String name, boolean ok) { System.out.println(name + "\t" + (ok ? "ok" : "FAIL")); }

    public static void main(String[] args) {
        long min = 60_000L;
        // set 10 minutes into the TV's life, for 5 minutes: it ends at wall 1000+5min
        long wall = 1_000_000_000L, since = 10 * min;
        check("running now, same boot", SleepWindow.running(wall + 5 * min, wall + min, since, since + min));
        check("over: the end has passed", !SleepWindow.running(wall + 5 * min, wall + 6 * min, since, since + 6 * min));
        // restart one minute later: the wall clock goes on (4 minutes left on it) but the TV has been up 20 seconds
        check("after a restart the timer is not shown", !SleepWindow.running(wall + 5 * min, wall + 2 * min, since, 20_000));
        check("a timer from before the update has no start: the clock decides", SleepWindow.running(wall + 5 * min, wall, 0, 20_000));
        check("no end saved is no timer", !SleepWindow.running(0, wall, 0, 20_000));
    }
}
