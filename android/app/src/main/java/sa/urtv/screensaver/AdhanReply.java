package sa.urtv.screensaver;

/**
 * The launcher's answer to ".../adhan" has the columns (prayer, any_key). A launcher that is older than this screensaver does not know the
 * path and answers with its «يُتلى الآن» row (playing, title, who) whenever a surah plays: that is not an adhan, and must never show the
 * bar or eat a key. No Android in it: tests/AdhanReplyRun.java runs it.
 */
final class AdhanReply {
    private AdhanReply() { }

    /** The row is an adhan row only when both of its columns are there. */
    static boolean valid(String[] columns) {
        boolean prayer = false, any = false;
        if (columns != null) {
            for (String c : columns) {
                if ("prayer".equals(c)) prayer = true;
                else if ("any_key".equals(c)) any = true;
            }
        }
        return prayer && any;
    }
}
