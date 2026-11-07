package sa.urtv.home.tilawah;

/** For tests/seek_burst_test.py: SeekBurst. Prints one line per check: name TAB ok. */
public final class SeekBurstRun {
    static void check(String name, boolean ok) { System.out.println(name + "\t" + (ok ? "ok" : "FAIL")); }

    public static void main(String[] args) {
        SeekBurst b = new SeekBurst();
        check("a single press is applied at once, as before", b.offer(10_000, 1000) == 10_000);
        check("the screen's pace (one step per 250 ms) all passes", b.offer(10_000, 1250) == 10_000 && b.offer(10_000, 1500) == 10_000 && b.offer(-10_000, 1750) == -10_000);

        b = new SeekBurst();
        int applied = 0, calls = 0;
        for (int i = 0; i < 1000; i++) { int d = b.offer(10_000, i); if (d != 0) { applied += d; calls++; } }   // 1000 presses in one second
        check("a flood is one seek per guard window: 10 in a second", calls == 10);
        check("a flood is never added up: 10 s per applied seek, no more", applied == 10 * 10_000);

        b = new SeekBurst();
        b.offer(10_000, 0);
        check("a press inside the guard is dropped", b.offer(10_000, SeekBurst.GUARD_MS - 1) == 0);
        check("at the guard it passes", b.offer(10_000, SeekBurst.GUARD_MS) == 10_000);

        b = new SeekBurst();
        b.offer(10_000, 0);
        b.reset();
        check("reset: the next press is a first press", b.offer(5_000, 1) == 5_000);

        check("zero delta does nothing", new SeekBurst().offer(0, 0) == 0);
        b = new SeekBurst();
        b.offer(1_000, 5_000);
        check("a clock that went backwards does not hold a press back", b.offer(1_000, 0) == 1_000);

        check("target clamps at the start", SeekBurst.target(3_000, -10_000, 100_000) == 0);
        check("target clamps at the duration", SeekBurst.target(95_000, 10_000, 100_000) == 100_000);
        check("target with no known duration only has the floor", SeekBurst.target(95_000, 10_000, 0) == 105_000);
        check("target does not overflow", SeekBurst.target(Integer.MAX_VALUE, Integer.MAX_VALUE, 0) > 0);
    }
}
