package sa.urtv.home.tilawah;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** For tests/dpad_rtl_test.py: CellWalk on tests/cell_walk_cases.tsv, one answer line per case line.
 *  next POSITION COUNT RIGHT(1/0) -> the cell or -1 | consume MOVED REPEAT ONCELL -> true/false */
public final class CellWalkRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            if (f[0].equals("next")) out.append(CellWalk.next(Integer.parseInt(f[1]), Integer.parseInt(f[2]), "1".equals(f[3]))).append('\n');
            else out.append(CellWalk.consume("1".equals(f[1]), Integer.parseInt(f[2]), "1".equals(f[3]))).append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
