package sa.urtv.home;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** For tests/hijri_offset_test.py: the launcher's HijriShift.java (the offset from the fragment, the shifted instant) on a plain JVM. */
public final class HijriShiftRun {
    private static int failed;

    private static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  -> " + detail));
        if (!ok) failed++;
    }

    private static String day(java.util.Date d, String tz) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone(tz));
        return f.format(d);
    }

    public static void main(String[] args) {
        check("no fragment is 0", HijriShift.fromFragment(null) == 0 && HijriShift.fromFragment("") == 0, "");
        check("a fragment without it is 0 (the default leaves everything as it was)", HijriShift.fromFragment("city=x&lat=1&lon=2&digits=hindi") == 0, "");
        check("hijri=-1 anywhere in the fragment", HijriShift.fromFragment("city=x&hijri=-1&digits=hindi") == -1 && HijriShift.fromFragment("hijri=-1") == -1
                && HijriShift.fromFragment("city=x&digits=hindi&hijri=-1") == -1, "");
        check("hijri=-2, 1, 2", HijriShift.fromFragment("a=1&hijri=-2") == -2 && HijriShift.fromFragment("a=1&hijri=1") == 1 && HijriShift.fromFragment("a=1&hijri=2") == 2, "");
        for (String bad : new String[] {"3", "-3", "x", "1.5", "", "+1", "10", "--1", "٢"})
            check("a bad value (" + bad + ") is 0", HijriShift.fromFragment("a=1&hijri=" + bad) == 0, String.valueOf(HijriShift.fromFragment("a=1&hijri=" + bad)));
        check("a key that only ends in hijri is not it", HijriShift.fromFragment("nothijri=1&xhijri=2") == 0, "");
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"), Locale.US);
        c.clear();
        c.set(2026, Calendar.MARCH, 7, 23, 30, 0);   // the clocks go forward the next night
        check("date: 0 is the calendar's own time", HijriShift.date(c, 0).equals(c.getTime()), "");
        check("date: +1 is the next calendar day at the same clock time, across a change of clocks (not 24 hours)",
                "2026-03-08 23:30".equals(day(HijriShift.date(c, 1), "America/New_York")), day(HijriShift.date(c, 1), "America/New_York"));
        check("date: +2 over the change of clocks", "2026-03-09 23:30".equals(day(HijriShift.date(c, 2), "America/New_York")), day(HijriShift.date(c, 2), "America/New_York"));
        check("date: does not change the calendar it was given", "2026-03-07 23:30".equals(day(c.getTime(), "America/New_York")), "");
        c.set(2026, Calendar.JANUARY, 1, 12, 0, 0);
        check("date: -1 over a month end", "2025-12-31 12:00".equals(day(HijriShift.date(c, -1), "America/New_York")), day(HijriShift.date(c, -1), "America/New_York"));
        System.exit(failed == 0 ? 0 : 1);
    }
}
