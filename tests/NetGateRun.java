package sa.urtv.home;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** For tests/netgate_test.py: NetGate.java on the cases in tests/netgate_cases.tsv. Arg: the cases file. One answer per case line. */
public final class NetGateRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            boolean r;
            switch (f[0]) {
                case "allowed": r = NetGate.allowed(b(f[1]), b(f[2])); break;   // allowed front display
                case "due": r = NetGate.due(b(f[1]), b(f[2]), Long.parseLong(f[3]), Long.parseLong(f[4]), Long.parseLong(f[5])); break;   // due front display last gap now
                case "background": r = NetGate.backgroundOk(b(f[1])); break;
                default: throw new IllegalArgumentException(f[0]);
            }
            out.append(r ? "yes" : "no").append('\n');
        }
        System.out.print(out);
    }

    private static boolean b(String s) { return "1".equals(s); }
}
