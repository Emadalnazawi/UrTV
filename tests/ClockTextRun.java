package sa.urtv.home;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** For tests/clock_text_test.py: ClockText.java on the cases in tests/clock_text_cases.tsv.
 *  Arg: the cases file. One answer per case line. */
public final class ClockTextRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            boolean h24 = "24".equals(f[2]), latin = "latin".equals(f[3]);
            String r;
            switch (f[0]) {
                case "time": r = ClockText.time(f[1], h24, latin); break;
                case "face": {   // the home screen's clock: hh:mm without the ص or م
                    String[] hm = f[1].split(":");
                    r = ClockText.face(Integer.parseInt(hm[0]), Integer.parseInt(hm[1]), h24, latin);
                    break;
                }
                case "suffix": r = "[" + ClockText.suffix(Integer.parseInt(f[1]), h24) + "]"; break;   // [] = none
                case "on": r = String.valueOf(ClockText.isOn(f[1])); break;
                case "sun": r = GreetingText.sun("الغروب", f[1], latin, h24); break;
                default: r = "?";
            }
            out.append(r).append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
