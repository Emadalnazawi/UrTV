package sa.urtv.home;

/** For tests/month_names_test.py: MonthNames.java on a plain JVM. Prints PASS/FAIL lines, then the table as TABLE lines. */
public final class MonthNamesRun {
    static int failed;
    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  -> " + detail));
        if (!ok) failed++;
    }
    public static void main(String[] args) {
        check("months=levant in the fragment, anywhere; anything else is the default",
                MonthNames.levant("city=x&months=levant&digits=latin") && MonthNames.levant("months=levant") && !MonthNames.levant("months=default")
                && !MonthNames.levant("city=x") && !MonthNames.levant(null) && !MonthNames.levant("xmonths=levant"), "");
        check("the home date line: «٢٧ أكتوبر» becomes «٢٧ تشرين الأول»", MonthNames.of("الأحد، ٤ ربيع الأول  –  ٢٧ أكتوبر", true).equals("الأحد، ٤ ربيع الأول  –  ٢٧ تشرين الأول"),
                MonthNames.of("٢٧ أكتوبر", true));
        check("off: the text as it is", MonthNames.of("٢٧ أكتوبر", false).equals("٢٧ أكتوبر"), "");
        check("the Hijri months stay (no Gregorian name is in them)", MonthNames.of("ربيع الأول رجب شعبان رمضان", true).equals("ربيع الأول رجب شعبان رمضان"), "");
        check("other spellings a TV may have: «إبريل», «يونيه», «يوليه»", MonthNames.of("٢ إبريل", true).equals("٢ نيسان")
                && MonthNames.of("يونيه يوليه", true).equals("حزيران تموز"), MonthNames.of("٢ إبريل", true));
        // what this JVM's Arabic data writes for each month (the home screen's SimpleDateFormat("d MMMM", ar)): every one becomes a Levant name
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d MMMM", new java.util.Locale("ar"));
        StringBuilder missed = new StringBuilder();
        java.util.Calendar c = java.util.Calendar.getInstance();
        for (int mo = 0; mo < 12; mo++) {
            c.set(2026, mo, 15);
            String out = MonthNames.of(f.format(c.getTime()), true), want = MonthNames.LEVANT[mo][1];
            if (!out.endsWith(want)) missed.append(f.format(c.getTime())).append(" -> ").append(out).append("; ");
        }
        check("this JVM's Arabic month names, each one to its Levant name", missed.length() == 0, missed.toString());
        for (String[] m : MonthNames.LEVANT) System.out.println("TABLE\t" + m[0] + "\t" + m[1]);
        System.exit(failed == 0 ? 0 : 1);
    }
}
