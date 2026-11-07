package sa.urtv.home.tilawah;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** For tests/prayer_crosscheck_test.py: PrayerMath.compute against tests/prayer_published.tsv, timetables printed by four authorities (not computed by any library). */
public final class PrayerPublishedRun {
    public static void main(String[] args) throws Exception {
        int rows = 0, bad = 0, worst = 0;
        StringBuilder detail = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t");
            String[] ymd = f[7].split("-");
            int y = Integer.parseInt(ymd[0]), mo = Integer.parseInt(ymd[1]), d = Integer.parseInt(ymd[2]), tol = Integer.parseInt(f[5]);
            long[] g = PrayerMath.compute(y, mo, d, Double.parseDouble(f[2]), Double.parseDouble(f[3]), f[1], "1".equals(f[6]), Integer.parseInt(f[4]));
            String[] want = f[8].split(" ");
            rows++;
            for (int i = 0; i < 6; i++) {
                int diff = PrayerCrosscheckRun.wallMinutes(g[i], f[1], y, mo, d) - PrayerCrosscheckRun.minutes(want[i]);
                worst = Math.max(worst, Math.abs(diff));
                if (Math.abs(diff) > tol) { bad++; detail.append("  ").append(f[0]).append(' ').append(f[7]).append(' ').append(PrayerCrosscheckRun.NAMES[i]).append(' ').append(diff).append(" min vs the printed ").append(want[i]).append('\n'); }
            }
        }
        System.out.print(detail);
        System.out.println((bad == 0 && rows > 0 ? "PASS " : "FAIL ") + "launcher PrayerMath vs the printed timetables of Makkah, Doha, Dubai and Kuwait: " + rows + " days within each source's own margin (worst " + worst + " min)");
        System.exit(bad == 0 && rows > 0 ? 0 : 1);
    }
}
