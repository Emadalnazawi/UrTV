package sa.urtv.home;

/**
 * When the home screen's own network fetches may run: only while the home screen is in front and the display is on, and a repeating
 * one not before its gap has passed. Pure (no Android), so the rule is tested on its own; the Android side asks it with
 * {@link NetGateAndroid#displayOn}. What does not ask it, on purpose: the adhan alarm itself (computed on the TV), a playback the person
 * started, the phone remote's update, and the screensaver (it is «in front and display on» by definition). docs/NETWORK.md says which is which.
 */
public final class NetGate {
    private NetGate() { }

    /** The home is in front and the display is on: the only state in which a refresh may start. */
    public static boolean allowed(boolean homeInFront, boolean displayOn) {
        return homeInFront && displayOn;
    }

    /**
     * A refresh that repeats: allowed, and the last try (lastFetchAt, 0 = never) is at least minGapMs old. A clock set back (a last try in
     * the future) counts as due, so a TV with a wrong date does not stop asking for good.
     */
    public static boolean due(boolean homeInFront, boolean displayOn, long lastFetchAt, long minGapMs, long now) {
        if (!allowed(homeInFront, displayOn)) return false;
        return lastFetchAt <= 0 || lastFetchAt > now || now - lastFetchAt >= minGapMs;
    }

    /** A fetch for something that must be right in the background (the adhan's times): skipped only with the display off, never for another app in front. */
    public static boolean backgroundOk(boolean displayOn) {
        return displayOn;
    }
}
