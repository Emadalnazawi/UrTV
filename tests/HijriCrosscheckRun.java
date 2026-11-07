import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.HijrahChronology;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;

/**
 * For tests/hijri_crosscheck_test.py: the Umm al-Qura Hijri date of every day of 1447-1450 AH (from tests/hijri_crosscheck.tsv: the official month
 * starts) in the JDK's own Hijrah-umalqura calendar and, when ICU4J is on the class path , in ICU4J's islamic-umalqura
 * calendar, asked the way the launcher asks (a Calendar for the city's zone, ULocale "en-u-ca-islamic-umalqura", at 00:05, noon and 23:55).
 * The launcher itself runs android.icu, which is ICU4J inside Android; this is the same table. Prints PASS/FAIL lines.
 */
public final class HijriCrosscheckRun {
    public static void main(String[] args) throws Exception {
        List<int[]> days = new ArrayList<>();   // gregorian y, m, d, then hijri y, m, d
        int months = 0;
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t");
            LocalDate start = LocalDate.parse(f[2]);
            months++;
            for (int k = 0; k < Integer.parseInt(f[3]); k++) {
                LocalDate g = start.plusDays(k);
                days.add(new int[] {g.getYear(), g.getMonthValue(), g.getDayOfMonth(), Integer.parseInt(f[0]), Integer.parseInt(f[1]), k + 1});
            }
        }
        int jdkBad = 0;
        StringBuilder detail = new StringBuilder();
        for (int[] d : days) {
            HijrahDate h = HijrahChronology.INSTANCE.date(LocalDate.of(d[0], d[1], d[2]));
            int y = h.get(ChronoField.YEAR), m = h.get(ChronoField.MONTH_OF_YEAR), dd = h.get(ChronoField.DAY_OF_MONTH);
            if (y != d[3] || m != d[4] || dd != d[5]) { jdkBad++; if (jdkBad <= 10) detail.append("  JDK ").append(d[0]).append('-').append(d[1]).append('-').append(d[2]).append(": official ").append(d[3]).append('-').append(d[4]).append('-').append(d[5]).append(", JDK ").append(y).append('-').append(m).append('-').append(dd).append('\n'); }
        }
        System.out.print(detail);
        System.out.println((jdkBad == 0 && days.size() > 1400 ? "PASS " : "FAIL ") + "JDK Hijrah-umalqura: all " + days.size() + " days of 1447-1450 AH (" + months + " months) equal the official month starts");
        boolean fail = jdkBad != 0 || days.size() < 1400;
        System.exit(fail ? 1 : 0);
    }
}
