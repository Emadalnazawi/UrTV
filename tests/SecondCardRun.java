package sa.urtv.screensaver;

/**
 * For tests/second_card_test.py: «بطاقة ثانية» (SecondCard.java) on a plain JVM. args[0] is the screensaver's cards, as
 * tests/second_card_test.py reads them from Prefs.PANELS (without the home-only next prayer), joined by commas.
 */
public final class SecondCardRun {
    static int failed;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  -> " + detail));
        if (!ok) failed++;
    }

    public static void main(String[] args) {
        String[] keys = args[0].split(",");
        String[][] cards = new String[keys.length][];
        for (int i = 0; i < keys.length; i++) cards[i] = new String[]{keys[i], keys[i]};

        // what the screen gets from what is stored
        check("read: none by default (nothing stored)", SecondCard.read(null, "verse", cards).equals("none"), SecondCard.read(null, "verse", cards));
        check("read: a card beside another first card is kept", SecondCard.read("prayers", "verse", cards).equals("prayers"), SecondCard.read("prayers", "verse", cards));
        check("read: never the first card again", SecondCard.read("verse", "verse", cards).equals("none"), SecondCard.read("verse", "verse", cards));
        check("read: never the next prayer (the bottom-right panel)", SecondCard.read("nextprayer", "verse", cards).equals("none"), SecondCard.read("nextprayer", "verse", cards));
        check("read: an unknown value is none", SecondCard.read("bogus", "verse", cards).equals("none") && SecondCard.read("", "verse", cards).equals("none"), "");
        check("read: no first card, no second one", SecondCard.read("prayers", "none", cards).equals("none"), SecondCard.read("prayers", "none", cards));
        boolean all = true;
        for (String[] c : cards) for (String[] f : cards) {
            String got = SecondCard.read(c[0], f[0], cards);
            boolean want = !c[0].equals("none") && !f[0].equals("none") && !c[0].equals(f[0]);
            if (!got.equals(want ? c[0] : "none")) { all = false; System.out.println("  " + c[0] + " beside " + f[0] + " -> " + got); }
        }
        check("read: every card beside every other first card, and none for the rest", all, "");

        // what the receiver refuses (null = kept)
        check("receiver: none is always fine", SecondCard.refuse("none", "verse", cards) == null && SecondCard.refuse("none", "none", cards) == null, "");
        check("receiver: another card is fine", SecondCard.refuse("hourly", "verse", cards) == null, String.valueOf(SecondCard.refuse("hourly", "verse", cards)));
        String np = SecondCard.refuse("nextprayer", "verse", cards);
        check("receiver: nextprayer is refused, and says it is the home screen's", np != null && np.startsWith("error: ") && np.contains("home-screen"), String.valueOf(np));
        String same = SecondCard.refuse("verse", "verse", cards);
        check("receiver: the first card again is refused", same != null && same.startsWith("error: ") && same.contains("first card"), String.valueOf(same));
        String bogus = SecondCard.refuse("bogus", "verse", cards);
        check("receiver: an unknown card is refused with the list (without nextprayer)", bogus != null && bogus.startsWith("error: panel2 is one of ")
            && bogus.contains(" hourly ") && !bogus.contains("nextprayer"), String.valueOf(bogus));
        String alone = SecondCard.refuse("hourly", "none", cards);
        check("receiver: no first card is refused, with the command to set one", alone != null && alone.contains("optimize/saver.sh card"), String.valueOf(alone));
        check("receiver: case matters (Hourly is not a card)", SecondCard.refuse("Hourly", "verse", cards) != null, "");

        // the settings' row
        check("settings row: beside the small clock with a first card", SecondCard.rowShown("small", "verse", cards), "");
        check("settings row: not beside the medium or the large clock", !SecondCard.rowShown("medium", "verse", cards) && !SecondCard.rowShown("large", "verse", cards), "");
        check("settings row: not without a first card", !SecondCard.rowShown("small", "none", cards), "");

        // the fragment
        check("fragment: nothing without a second card, so the URL is as it was", SecondCard.fragment("none", cards).isEmpty() && SecondCard.fragment("bogus", cards).isEmpty(), "");
        check("fragment: &panel2= with one", SecondCard.fragment("hourly", cards).equals("&panel2=hourly"), SecondCard.fragment("hourly", cards));
        if (failed > 0) { System.out.println(failed + " failed"); System.exit(1); }
    }
}
