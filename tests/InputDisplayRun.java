package sa.urtv.home;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * For tests/input_display_test.py: InputDisplay.java on tests/input_display_cases.tsv. A line is
 *   cec     RAW                      -> the cleaned CEC name, "" for none (\n in RAW is a newline, \t a tab, a backslash u and four hex digits a character)
 *   name    CUSTOM  CEC  OWN         -> the name without the state ("-" in CUSTOM or CEC = null)
 *   label   CUSTOM  CEC  OWN  STATE   -> the row's text; STATE is standby | on
 */
public final class InputDisplayRun {
    static String arg(String s) { return s.equals("-") ? null : InputNamesRun.unescape(s); }

    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "cec": out.append(InputDisplay.cecName(arg(f[1]))); break;
                case "name": out.append(InputDisplay.name(arg(f[1]), arg(f[2]), InputNamesRun.unescape(f[3]))); break;
                default: out.append(InputDisplay.label(arg(f[1]), arg(f[2]), InputNamesRun.unescape(f[3]), f[4].equals("standby")));
            }
            out.append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
