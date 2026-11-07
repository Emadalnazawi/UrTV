package sa.urtv.home.tilawah;

/**
 * What the person asked the player to do, kept apart from what the MediaPlayer is doing, with no Android in it (tests/PlayIntentRun.java
 * runs it). Preparing media takes a while; a Pause (or a Stop, or another pick) in that while must not be undone by the «prepared», focus or
 * retry callback that arrives later. Every player that is started gets a generation; its callbacks ask whether it is still the current one.
 */
final class PlayIntent {
    private boolean want;
    private int generation;

    /** a surah, station or adhan was picked, or Resume was pressed: playing is wanted */
    void ask() { want = true; }

    /** Pause: playing is not wanted; media that is still loading stays ready for an explicit Resume */
    void pause() { want = false; }

    boolean wants() { return want; }

    /** a new player is being started: the older ones' callbacks are stale from now on. Returns this player's generation. */
    int newPlayer() { return ++generation; }

    /** Stop: nothing is wanted and every player's callbacks are stale */
    void stop() {
        want = false;
        generation++;
    }

    boolean current(int gen) { return gen == generation; }

    /** the generation of the player that is current now, for a timer that is set while it is */
    int currentGeneration() { return generation; }

    /** The media is ready: it starts only if it is the current player and playing is still wanted (otherwise it waits, paused). */
    boolean startOnPrepared(int gen) { return current(gen) && want; }

    /** A retry timer fires: it restarts the same media only if nothing has been picked, paused-and-left or stopped since, and it is not playing. */
    boolean retry(int gen, boolean playing) { return current(gen) && !playing; }
}
