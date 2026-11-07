package sa.urtv.home.tilawah;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * For tests/prayer_crosscheck_test.py: PrayerMath.compute (the launcher's arithmetic) against tests/prayer_crosscheck.tsv, the times of every
 * city of the app from the independent reference (a port of adhan-js, see tests/prayer_crosscheck_gen.mjs). Each of the six times may differ
 * from the reference by one minute (both round to the minute; the reference is Meeus' full series, this is the short one).
 * A row whose zone has another UTC offset on this machine than on the one that wrote the file (another time-zone database) is re-based by
 * the difference and counted apart from formula errors; only a zone this machine does not know at all is skipped.
 * Prints PASS/FAIL lines and a summary of how far the two are apart.
 */
public final class PrayerCrosscheckRun {
    static final String[] NAMES = {"fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"};

    static int wallMinutes(long at, String tz, int y, int mo, int d) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone(tz), Locale.US);
        c.setTimeInMillis(at);
        Calendar day = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        day.clear();
        day.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        Calendar want = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        want.clear();
        want.set(y, mo - 1, d);
        long days = Math.round((day.getTimeInMillis() - want.getTimeInMillis()) / 86400000.0);
        return (int) (days * 1440 + c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE));
    }

    static int minutes(String hhmm) { return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3)); }

    public static void main(String[] args) throws Exception {
        int tolerance = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        int rows = 0, skipped = 0, bad = 0, wrongMethod = 0, exact = 0, off1 = 0, rebased = 0;
        java.util.TreeSet<String> driftZones = new java.util.TreeSet<>();
        int[] worst = new int[6];
        int[][] hist = new int[6][5];   // signed difference -2..+2 and beyond clipped
        StringBuilder detail = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t");
            String tz = f[0];
            String[] ymd = f[4].split("-");
            int y = Integer.parseInt(ymd[0]), mo = Integer.parseInt(ymd[1]), d = Integer.parseInt(ymd[2]);
            int method = Integer.parseInt(f[6]);
            long noon = PrayerMath.midnight(y, mo, d, tz) + 12 * 3600_000L;
            if (!TimeZone.getTimeZone(tz).getID().equals(tz)) { skipped++; continue; }   // a zone this machine does not know at all
            // this machine's time-zone data gives the zone another offset that day than the data that wrote the file (Morocco left UTC+1 in
            // tzdata 2026c): the sun is the same, only the clock moved, so the reference is re-based by the difference, counted apart
            int shift = TimeZone.getTimeZone(tz).getOffset(noon) / 60000 - Integer.parseInt(f[7]);
            if (shift != 0) { rebased++; driftZones.add(tz); }
            String pm = method == 0 ? "jafari" : method == 8 && PrayerMath.methodFor(tz, null) != 8 ? "gulf" : null;   // the rows chosen by hand (#pm=): the others are the zone's own
            if (PrayerMath.methodFor(tz, pm) != method) { wrongMethod++; System.out.println("FAIL method " + f[1] + " " + tz + ": app picks " + PrayerMath.methodFor(tz, pm) + ", reference " + method); }
            long[] g = PrayerMath.compute(y, mo, d, Double.parseDouble(f[2]), Double.parseDouble(f[3]), tz, "1".equals(f[5]), method);
            String[] want = f[8].split(" ");
            rows++;
            boolean rowBad = false;
            for (int i = 0; i < 6; i++) {
                int diff = wallMinutes(g[i], tz, y, mo, d) - (minutes(want[i]) + shift);
                hist[i][Math.max(-2, Math.min(2, diff)) + 2]++;
                worst[i] = Math.max(worst[i], Math.abs(diff));
                if (Math.abs(diff) > tolerance) { rowBad = true; if (bad < 40) detail.append("  ").append(f[1]).append(' ').append(tz).append(' ').append(f[4]).append(" m").append(method).append(' ').append(NAMES[i]).append(' ').append(diff > 0 ? "+" : "").append(diff).append(" min (app ").append(String.format("%02d:%02d", wallMinutes(g[i], tz, y, mo, d) / 60, wallMinutes(g[i], tz, y, mo, d) % 60)).append(", reference ").append(want[i]).append(")\n"); }
            }
            if (rowBad) bad++;
        }
        StringBuilder sum = new StringBuilder();
        for (int i = 0; i < 6; i++) sum.append(NAMES[i]).append(" max ").append(worst[i]).append(" [-2or less ").append(hist[i][0]).append(", -1 ").append(hist[i][1]).append(", 0 ").append(hist[i][2]).append(", +1 ").append(hist[i][3]).append(", +2or more ").append(hist[i][4]).append("]; ");
        System.out.println("rows " + rows + ", skipped for a zone this machine does not know " + skipped + "; " + sum);
        System.out.println("time-zone data: this machine " + java.time.zone.ZoneRulesProvider.getVersions("UTC").lastEntry().getKey() + "; "
                + (rebased == 0 ? "every row at the offset the file was written with" : rebased + " rows re-based for this machine's own offsets (" + String.join(", ", driftZones) + "), not formula errors"));
        System.out.print(detail);
        System.out.println((bad == 0 && wrongMethod == 0 ? "PASS " : "FAIL ") + "launcher PrayerMath vs the reference: every time of " + rows + " city-days within " + tolerance + " minute" + (bad > 0 ? " -> " + bad + " rows differ more" : ""));
        System.out.println((skipped * 50 <= rows ? "PASS " : "FAIL ") + "launcher crosscheck: at most 2% of the rows skipped for time-zone data (" + skipped + " of " + (rows + skipped) + ")");
        System.exit(bad == 0 && wrongMethod == 0 && skipped * 50 <= rows ? 0 : 1);
    }
}
