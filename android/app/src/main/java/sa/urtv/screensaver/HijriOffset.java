package sa.urtv.screensaver;

/**
 * «فرق التاريخ الهجري»: where the months start by moon sighting (Egypt, Jordan, the Levant, Morocco...), the Hijri day can be a day or
 * two off Umm al-Qura. The setting moves the Hijri date by whole days, -2 to +2, default 0. It goes to the page and the home screen in
 * the settings fragment as {@code hijri=-1}, only while it is not zero, so a TV that never set it sends the same fragment as always.
 * Plain Java on purpose: tests/HijriOffsetRun.java runs it on a JVM.
 */
final class HijriOffset {
    static final int MAX = 2;

    private HijriOffset() { }

    /** A value from the shell: "-2".."2" or "+1"; null when it is not a whole number in range. */
    static Integer parse(String s) {
        if (s == null || !s.matches("[+-]?\\d")) return null;
        int v = Integer.parseInt(s.startsWith("+") ? s.substring(1) : s);
        return v < -MAX || v > MAX ? null : v;
    }

    /** The saved value; anything that is not a number in range is 0. */
    static int decode(int saved) { return saved < -MAX || saved > MAX ? 0 : saved; }

    /** What goes in the settings fragment: nothing at 0 (the page and the home screen are then exactly as they were). */
    static String fragment(int v) { return v == 0 ? "" : "&hijri=" + v; }

    /** "0", "+1", "-1" for optimize/saver.sh show */
    static String describe(int v) { return v > 0 ? "+" + v : String.valueOf(v); }
}
