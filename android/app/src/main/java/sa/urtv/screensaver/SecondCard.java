package sa.urtv.screensaver;

/**
 * «بطاقة ثانية»: a second card beside the screensaver's clock, only while the clock is small (there is room for two cards beside it,
 * not beside a larger one). It is any of the screensaver's cards (Prefs.SAVER_PANELS: Prefs.PANELS without the next prayer, which the
 * bottom-right panel says already) but the first card again. None by default, and with no first card there is no second one.
 * It goes to the page in the settings fragment as {@code &panel2=}, only while there is one, so without it the fragment is exactly
 * what it was; the page shows it beside the small clock and ignores it otherwise. Plain Java on purpose (the cards are passed in):
 * tests/SecondCardRun.java runs it on a JVM.
 */
final class SecondCard {
    private SecondCard() { }

    private static boolean known(String v, String[][] cards) {
        if (v == null) return false;
        for (String[] p : cards) if (p[0].equals(v)) return true;
        return false;
    }

    /** The second card the screen gets from what is stored: "none" when there is none, or it may not be one beside this first card. */
    static String read(String stored, String first, String[][] cards) {
        if (!known(stored, cards) || "none".equals(stored) || stored.equals(first)) return "none";
        return known(first, cards) && !"none".equals(first) ? stored : "none";
    }

    /** What the receiver says to a second card it will not keep, or null when it is fine (none always is). */
    static String refuse(String v, String first, String[][] cards) {
        if ("none".equals(v)) return null;
        if ("nextprayer".equals(v)) return "error: nextprayer is a home-screen card only (the screensaver's bottom-right panel already shows the next prayer)";
        if (!known(v, cards)) return "error: panel2 is one of " + names(cards);
        if (!known(first, cards) || "none".equals(first)) return "error: panel2 needs a first card beside the clock: optimize/saver.sh card verse";
        if (v.equals(first)) return "error: panel2 is " + v + ", the first card already: pick another one, or none";
        return null;
    }

    /** The settings' «بطاقة ثانية» row: only beside the small clock, and only when there is a first card. */
    static boolean rowShown(String clockSize, String first, String[][] cards) {
        return "small".equals(clockSize) && known(first, cards) && !"none".equals(first);
    }

    /** What goes in the settings fragment: nothing without a second card (the page and the URL are then as they were). */
    static String fragment(String second, String[][] cards) {
        return "none".equals(second) || !known(second, cards) ? "" : "&panel2=" + second;
    }

    private static String names(String[][] cards) {
        StringBuilder b = new StringBuilder();
        for (String[] p : cards) b.append(b.length() > 0 ? " " : "").append(p[0]);
        return b.toString();
    }
}
