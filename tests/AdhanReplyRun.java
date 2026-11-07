package sa.urtv.screensaver;

/** For tests/adhan_reply_test.py: AdhanReply.valid on the columns of the answers the launcher can give. */
public final class AdhanReplyRun {
    static void check(String name, boolean ok) { System.out.println(name + "\t" + (ok ? "ok" : "FAIL")); }

    public static void main(String[] args) {
        check("this launcher's adhan row (prayer, any_key) is an adhan", AdhanReply.valid(new String[]{"prayer", "any_key"}));
        check("an older launcher's row (playing, title, who) is not an adhan", !AdhanReply.valid(new String[]{"playing", "title", "who"}));
        check("one of the two columns is not enough", !AdhanReply.valid(new String[]{"prayer"}) && !AdhanReply.valid(new String[]{"any_key", "title"}));
        check("no columns, or null, is not an adhan", !AdhanReply.valid(new String[0]) && !AdhanReply.valid(null));
    }
}
