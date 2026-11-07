package sa.urtv.home;

/**
 * The «جرس التحديثات» on the home screen's top row, with no Android in it (tests/UpdatesBellRun.java runs it). The bell is there only
 * while there is real evidence that the Play Store has app updates waiting, so the question «is there evidence» is a Source that answers
 * WAITING, NONE or UNKNOWN, and UNKNOWN counts as no bell: nothing is ever guessed.
 *
 * Idea from Matinee (tv-vibehack), via talkies (https://github.com/rajupp/talkies, MIT, (c) 2026 TechPP) Updates.java, see
 * THIRD_PARTY_NOTICES.md. They read the Play Store's own notifications through a notification listener; that needs «notification access»,
 * which a person grants by hand in the TV's settings (not an install-time permission), so this code base does not: PlayStoreUpdates says
 * UNKNOWN until a source that works with normal permissions exists.
 */
final class UpdatesBell {
    enum Evidence { WAITING, NONE, UNKNOWN }

    /** Where the evidence comes from; asked on the main thread, so it must answer without waiting for anything slow. */
    interface Source { Evidence read(); }

    static final String PLAY_STORE = "com.android.vending";
    /** at most this often while the home screen is in front (a return to the front always asks) */
    static final long EVERY_MS = 6L * 60 * 60 * 1000;
    /**
     * What opens the Play Store's updates screen, in the order to try: the store's own «my apps» link, then the store itself. Each is an
     * ACTION_VIEW restricted to the Play Store's package; the last (null) means its launcher entry.
     */
    static final String[] OPEN_URIS = {"market://myapps", null};

    private final Source source;
    private Evidence last = Evidence.UNKNOWN;
    private long lastAt;
    private boolean asked;

    UpdatesBell(Source source) { this.source = source; }

    /** Whether the bell is in the row: wanted by the setting, a person who is not a child, the store is there and may be opened, and evidence says WAITING. */
    static boolean shown(boolean setting, boolean kids, boolean storeUsable, Evidence e) {
        return setting && !kids && storeUsable && e == Evidence.WAITING;
    }

    /** Whether to ask the source now: on return to the front (force), or when the last answer is 6 hours old. A clock that went back asks again. */
    boolean due(boolean force, long now) {
        return force || !asked || now < lastAt || now - lastAt >= EVERY_MS;
    }

    /** The evidence, asking the source only when due; a source that throws is UNKNOWN. */
    Evidence evidence(boolean force, long now) {
        if (!due(force, now)) return last;
        Evidence e;
        try { e = source.read(); } catch (RuntimeException ex) { e = Evidence.UNKNOWN; }
        last = e == null ? Evidence.UNKNOWN : e;
        lastAt = now;
        asked = true;
        return last;
    }
}
