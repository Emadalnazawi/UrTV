package sa.urtv.home.tilawah;

/**
 * After a command to MusicService, has it left nothing to keep a foreground service for? A pause with nothing loaded, a sleep
 * timer of 0 minutes or an unknown reciter start nothing; the service then stops instead of staying foreground with a
 * notification until the process dies. Pure logic (no Android classes) so tests/idle_command_test.py can run it.
 */
final class IdleCommand {
    private IdleCommand() { }

    /**
     * @param mayLeaveNothing false for the commands that start playback by their own course (radio, adhan) and for stop
     * @param sourceNone      nothing is chosen to play
     * @param hasPlayer       a MediaPlayer exists
     * @param sleepUntil      the in-player sleep timer's end, 0 when none
     */
    static boolean leavesNothing(boolean mayLeaveNothing, boolean sourceNone, boolean hasPlayer, long sleepUntil) {
        return mayLeaveNothing && sourceNone && !hasPlayer && sleepUntil == 0;
    }
}
