package sa.urtv.home;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** For tests/adhan_keys_test.py: AdhanKeys and AdhanBarText.playing on tests/adhan_keys_cases.tsv. One answer line per case line.
 *  "key": kind keyCode action repeat playing barShowing barFocused stopAny, answered PASS, STOP or EAT (one AdhanKeys lives from one
 *  "reset" line to the next). "bar": kind prayer friday stopAny, answered with the bar's words. */
public final class AdhanKeysRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        AdhanKeys keys = new AdhanKeys();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            if (f[0].equals("reset")) { keys = new AdhanKeys(); out.append("-\n"); continue; }
            if (f[0].equals("bar")) {
                out.append(AdhanBarText.playing(Integer.parseInt(f[1]), "1".equals(f[2]), "1".equals(f[3]))).append('\n');
                continue;
            }
            int r = keys.decide(Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), "1".equals(f[4]), "1".equals(f[5]), "1".equals(f[6]), "1".equals(f[7]));
            out.append(r == AdhanKeys.STOP ? "STOP" : r == AdhanKeys.EAT ? "EAT" : "PASS").append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
