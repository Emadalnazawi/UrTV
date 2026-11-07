package sa.urtv.screensaver;

/**
 * How often a WebView whose renderer process died (onRenderProcessGone) is made again, with no Android in it (tests/RenderGuardRun.java
 * runs it; the same file is in both apps). The dead view is thrown away, never reloaded; a new one is made only while that has not happened
 * more than twice in ten minutes. After that the screen does without it (the dream finishes, the home card stays off) until the ten minutes pass.
 */
final class RenderGuard {
    static final int MAX_RECREATIONS = 2;
    static final long WINDOW_MS = 10 * 60_000L;

    private final long[] deaths = new long[MAX_RECREATIONS + 1];
    private int count;

    /** a renderer died now */
    synchronized void gone(long now) {
        prune(now);
        if (count == deaths.length) {   // more than we count: keep the newest ones
            System.arraycopy(deaths, 1, deaths, 0, count - 1);
            count--;
        }
        deaths[count++] = now;
    }

    /** true when a new view may be made: it has died at most MAX_RECREATIONS times in the last WINDOW_MS */
    synchronized boolean mayCreate(long now) {
        prune(now);
        return count <= MAX_RECREATIONS;
    }

    private void prune(long now) {
        int keep = 0;
        for (int i = 0; i < count; i++) if (now - deaths[i] < WINDOW_MS) deaths[keep++] = deaths[i];
        count = keep;
    }
}
