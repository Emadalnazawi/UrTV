package sa.urtv.screensaver;

/** The screensaver's «فرق التاريخ الهجري» (HijriOffset.java) on a plain JVM; prints PASS/FAIL lines. */
public final class HijriOffsetRun {
    private static int failed;

    private static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  -> " + detail));
        if (!ok) failed++;
    }

    public static void main(String[] args) {
        for (String s : new String[] {"-2", "-1", "0", "1", "+1", "+2", "2"})
            check("parse " + s, HijriOffset.parse(s) != null && HijriOffset.parse(s) == Integer.parseInt(s.replace("+", "")), String.valueOf(HijriOffset.parse(s)));
        for (String s : new String[] {"3", "-3", "+3", "10", "", "x", "1.5", "١", "--1", " 1", null})
            check("refused " + s, HijriOffset.parse(s) == null, String.valueOf(HijriOffset.parse(s)));
        check("fragment 0 is nothing (the fragment stays as it was)", HijriOffset.fragment(0).isEmpty(), HijriOffset.fragment(0));
        check("fragment -1", "&hijri=-1".equals(HijriOffset.fragment(-1)), HijriOffset.fragment(-1));
        check("fragment 2", "&hijri=2".equals(HijriOffset.fragment(2)), HijriOffset.fragment(2));
        check("decode keeps -2..2", HijriOffset.decode(-2) == -2 && HijriOffset.decode(2) == 2 && HijriOffset.decode(0) == 0, "");
        check("decode: out of range is 0", HijriOffset.decode(3) == 0 && HijriOffset.decode(-9) == 0, "");
        check("describe", "+1".equals(HijriOffset.describe(1)) && "-2".equals(HijriOffset.describe(-2)) && "0".equals(HijriOffset.describe(0)), "");
        System.exit(failed == 0 ? 0 : 1);
    }
}
