package sa.urtv.home.tilawah;

/**
 * A flood guard for SEEK_BY: the screen already paces a held key (PlayerActivity: one step of 10 s every 250 ms), so this only keeps
 * a burst that gets past it, a script or a stuck key, from becoming dozens of MediaPlayer.seekTo calls a second (the stutter).
 * A press is applied unless the last applied one was less than GUARD_MS ago; the ones in between are dropped, not added up, so a
 * held key never moves faster than the screen's pace. No Android in it (tests/SeekBurstRun.java runs it); the service owns the clock.
 */
final class SeekBurst {
    /** at most one seek per this many ms; well under the screen's 250 ms, so its steps all pass */
    static final long GUARD_MS = 100;

    private long lastApply = Long.MIN_VALUE;

    /** A press at `now` (ms, a monotonic clock): the delta to apply, or 0 when it is dropped. */
    int offer(int delta, long now) {
        if (delta == 0) return 0;
        if (lastApply != Long.MIN_VALUE && now >= lastApply && now - lastApply < GUARD_MS) return 0;
        lastApply = now;
        return delta;
    }

    /** The source changed or playback stopped: the next press counts as the first. */
    void reset() { lastApply = Long.MIN_VALUE; }

    /** `position` moved by `delta`, kept inside 0..duration (duration <= 0: not known, only the floor applies). */
    static int target(int position, int delta, int duration) {
        long t = (long) position + delta;
        if (t < 0) t = 0;
        if (duration > 0 && t > duration) t = duration;
        if (t > Integer.MAX_VALUE) t = Integer.MAX_VALUE;
        return (int) t;
    }
}
