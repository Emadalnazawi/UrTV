import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * For tests/hijri_crosscheck_test.py, compiled and run only when ICU4J_JAR names an icu4j jar (any version from 58 to 76 gives the same answer): every day
 * of 1447-1450 AH of tests/hijri_crosscheck.tsv asked the way the launcher asks (MainActivity, PrayerTimes.hijriMonth: a Calendar for the city's zone,
 * ULocale "en-u-ca-islamic-umalqura"), at 00:05, noon and 23:55. The launcher runs android.icu, which is ICU4J inside Android.
 */
public final class HijriIcuRun {
    public static void main(String[] args) throws Exception {
        int days = 0, calls = 0, bad = 0;
        StringBuilder detail = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t");
            LocalDate start = LocalDate.parse(f[2]);
            for (int k = 0; k < Integer.parseInt(f[3]); k++) {
                LocalDate g = start.plusDays(k);
                days++;
                for (int[] hm : new int[][] {{0, 5}, {12, 0}, {23, 55}}) {
                    long at = ZonedDateTime.of(g.getYear(), g.getMonthValue(), g.getDayOfMonth(), hm[0], hm[1], 0, 0, ZoneId.of("Asia/Riyadh")).toInstant().toEpochMilli();
                    com.ibm.icu.util.Calendar c = com.ibm.icu.util.Calendar.getInstance(com.ibm.icu.util.TimeZone.getTimeZone("Asia/Riyadh"), new com.ibm.icu.util.ULocale("en-u-ca-islamic-umalqura"));
                    c.setTimeInMillis(at);
                    calls++;
                    int y = c.get(com.ibm.icu.util.Calendar.YEAR), m = c.get(com.ibm.icu.util.Calendar.MONTH) + 1, d = c.get(com.ibm.icu.util.Calendar.DAY_OF_MONTH);
                    if (y != Integer.parseInt(f[0]) || m != Integer.parseInt(f[1]) || d != k + 1) { bad++; if (bad <= 10) detail.append("  ").append(g).append(' ').append(hm[0]).append(':').append(hm[1]).append(": official ").append(f[0]).append('-').append(f[1]).append('-').append(k + 1).append(", ICU4J ").append(y).append('-').append(m).append('-').append(d).append('\n'); }
                }
            }
        }
        System.out.print(detail);
        System.out.println((bad == 0 && days > 1400 ? "PASS " : "FAIL ") + "ICU4J " + com.ibm.icu.util.VersionInfo.ICU_VERSION + " islamic-umalqura, asked as the launcher asks: " + calls + " asks over " + days + " days equal the official month starts");
        System.exit(bad == 0 && days > 1400 ? 0 : 1);
    }
}
