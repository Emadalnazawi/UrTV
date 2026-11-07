package sa.urtv.home;

/**
 * For tests/spring_test.py: Spring on a plain JVM, at 60 frames a second. Prints one line per measure:
 * "name value" (numbers in the spring's own unit, times in seconds).
 */
public final class SpringRun {
    static final float DT = 1 / 60f;

    public static void main(String[] args) {
        // the page: 0 -> 600 px, no bounce
        Spring page = new Spring(0.34f, 1f, 0, 0.25f);
        page.target = 600;
        float t = 0, peak = 0;
        while (page.step(DT) && t < 5) { t += DT; peak = Math.max(peak, page.value); }
        System.out.println("page_settle " + t);
        System.out.println("page_peak " + peak);
        System.out.println("page_end " + page.value);

        // a card's size: 1 -> 1.1 with the focus's little bounce, then back to 1
        Spring card = new Spring(0.34f, 0.74f, 1f);
        card.target = 1.1f;
        t = 0; peak = 0;
        while (card.step(DT) && t < 5) { t += DT; peak = Math.max(peak, card.value); }
        System.out.println("card_settle " + t);
        System.out.println("card_peak " + peak);
        System.out.println("card_end " + card.value);

        // a new target mid-flight keeps the speed it has
        Spring s = new Spring(0.34f, 1f, 0, 0.25f);
        s.target = 300;
        for (int i = 0; i < 6; i++) s.step(DT);
        float before = s.velocity;
        s.target = 600;
        System.out.println("retarget_speed_before " + before);
        System.out.println("retarget_speed_after " + s.velocity);
        s.step(DT);
        System.out.println("retarget_speed_next " + s.velocity);

        // a held arrow: the row's target moves 300 px every 120 ms (the home's repeat pace), eight times: the speed never falls to
        // nothing between two presses, and once let go the page comes to rest on the last target
        Spring held = new Spring(0.34f, 1f, 0, 0.25f);
        float slowest = Float.MAX_VALUE;
        for (int press = 1; press <= 8; press++) {
            held.target = press * 300;
            for (int f = 0; f < 7; f++) { held.step(DT); if (press > 1) slowest = Math.min(slowest, held.velocity); }   // 7 frames = 117 ms
        }
        t = 0;
        while (held.step(DT) && t < 5) t += DT;
        System.out.println("held_slowest " + slowest);
        System.out.println("held_end " + held.value);
        System.out.println("held_rest " + t);

        // a slow frame (50 ms, the cap the home uses) is as good as three 1/60 s ones: no jump, no blow-up
        Spring a = new Spring(0.34f, 1f, 0, 0.25f), b = new Spring(0.34f, 1f, 0, 0.25f);
        a.target = b.target = 600;
        a.step(0.05f);
        for (int i = 0; i < 3; i++) b.step(0.05f / 3);
        System.out.println("slow_frame_diff " + Math.abs(a.value - b.value));

        // settled: snaps to the target and stands still
        Spring z = new Spring(0.34f, 1f, 5, 0.25f);
        System.out.println("rest_moving " + (z.step(DT) ? 1 : 0));
        z.snap(42);
        System.out.println("snap " + z.value + " " + z.velocity + " " + z.target);
    }
}
