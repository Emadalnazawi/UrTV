package sa.urtv.home.tilawah;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * For tests/now_playing_test.py: NowPlayingText.java on tests/now_playing_cases.tsv. A line is
 *   title  radio(0|1) SURAH STATION         -> the first line
 *   sub    radio(0|1) RECITER SLEEPMINUTES  -> the second line
 *   note   ERROR BUFFERING(0|1)            -> the note in front of the second line (empty: none)
 *   subnote radio(0|1) RECITER SLEEPMINUTES:NOTE -> the second line with the note
 *   hint   radio(0|1)                       -> the second line while the card has the focus
 */
public final class NowPlayingTextRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            boolean radio = "1".equals(f[1]);
            switch (f[0]) {
                case "title": out.append(NowPlayingText.title(radio, f[2], f[3])); break;
                case "sub": out.append(NowPlayingText.sub(radio, f[2], Integer.parseInt(f[3]))); break;
                case "note": out.append(NowPlayingText.note(f[1], "1".equals(f[2]))); break;
                case "subnote": { String[] m = f[3].split(":", 2); out.append(NowPlayingText.sub(radio, f[2], Integer.parseInt(m[0]), m.length > 1 ? m[1] : "")); break; }
                default: out.append(NowPlayingText.hint(radio));
            }
            out.append('\n');
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
