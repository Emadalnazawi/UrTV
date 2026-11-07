package sa.urtv.home;

import java.util.Calendar;
import java.util.Date;

/**
 * «فرق التاريخ الهجري»: the Hijri date moved by whole days, -2 to +2, for places that start the month by moon sighting. The screensaver's
 * settings hand it over in the fragment as {@code hijri=-1}; nothing there (it is 0, an older screensaver, anything that is not a whole
 * number in range) means no shift. The page reads the same text (CONFIG.hijri). Every place that asks the Umm al-Qura calendar for a
 * date (the home screen's date line) formats {@code now + offset days} instead of now, so there is one way to shift.
 * No Android in here, so tests/hijri_offset_test.py runs it.
 */
public final class HijriShift {
    public static final int MAX = 2;

    private HijriShift() { }

    /** The value of {@code hijri=} in a settings fragment ("city=..&hijri=-1&.."); 0 when it is missing or not a whole number in range. */
    public static int fromFragment(String fragment) {
        if (fragment == null) return 0;
        for (String part : fragment.split("&")) {
            if (part.startsWith("hijri=")) return parse(part.substring(6));
        }
        return 0;
    }

    static int parse(String s) {
        if (s == null || !s.matches("-?\\d")) return 0;
        int v = Integer.parseInt(s);
        return v < -MAX || v > MAX ? 0 : v;
    }

    /** The same for a calendar in the city's zone: whole calendar days, so a change of clock cannot move it by an hour. At 0 it is {@code c.getTime()}. */
    public static Date date(Calendar c, int days) {
        if (days == 0) return c.getTime();
        Calendar m = (Calendar) c.clone();
        m.add(Calendar.DAY_OF_MONTH, days);
        return m.getTime();
    }
}
