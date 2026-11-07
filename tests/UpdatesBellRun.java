package sa.urtv.home;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * For tests/updates_bell_test.py: UpdatesBell.java on tests/updates_bell_cases.tsv, one answer line per case line.
 *   shown SETTING KIDS STORE EVIDENCE(WAITING|NONE|UNKNOWN)                    -> yes or no
 *   asks  FORCE@NOW,FORCE@NOW,...  EVIDENCE the source gives (or THROW)       -> how many times the source was asked, and the last answer
 */
public final class UpdatesBellRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            if (f[0].equals("shown")) {
                out.append(UpdatesBell.shown("1".equals(f[1]), "1".equals(f[2]), "1".equals(f[3]), UpdatesBell.Evidence.valueOf(f[4])) ? "yes" : "no");
            } else {
                final int[] n = {0};
                final String give = f[2];
                UpdatesBell b = new UpdatesBell(() -> {
                    n[0]++;
                    if (give.equals("THROW")) throw new IllegalStateException("x");
                    return give.equals("NULL") ? null : UpdatesBell.Evidence.valueOf(give);
                });
                UpdatesBell.Evidence last = null;
                for (String step : f[1].split(",")) {
                    String[] s = step.split("@");
                    last = b.evidence("1".equals(s[0]), Long.parseLong(s[1]));
                }
                out.append(n[0]).append(' ').append(last);
            }
            out.append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
