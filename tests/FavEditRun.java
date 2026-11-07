package sa.urtv.home;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

/** For tests/fav_edit_test.py: FavEdit (the «تعديل المفضلة» state machine), the PIN gate that guards entering it and the adhan rule over its keys,
 *  on tests/fav_edit_cases.tsv. One answer line per case line; a "new" line starts a fresh row. Columns are the command and its arguments,
 *  then the expected answer and a note (the test reads those two from the end).
 *  new MIN FOCUS PKGS | ok|back|undo|down NOW | move NOW DELTA | up NOW PERROW | key NOW CODE RTL (a left/right key, as the home screen maps it) |
 *  expire NOW | step CODE RTL | repeat REPEAT LAST NOW GAP | once REPEAT | gate PINSET LOCKED OPENED | adhanreset | adhan CODE ACTION REPEAT
 *  A FavEdit answer is RESULT|row|at|flags: flags c = carrying, x = on a ×, u = on «تراجع», "-" none. */
public final class FavEditRun {
    static final String[] NAMES = {"NOTHING", "FOCUS", "PICKED", "MOVED", "DROPPED", "CANCELLED", "REMOVED", "TOO_FEW", "UNDONE", "EXIT"};

    static String state(int result, FavEdit e) {
        String flags = (e.carrying() ? "c" : "") + (e.onX() ? "x" : "") + (e.onUndo() ? "u" : "");
        return NAMES[result] + "|" + String.join(",", e.row()) + "|" + e.at() + "|" + (flags.isEmpty() ? "-" : flags);
    }

    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        FavEdit e = null;
        AdhanKeys keys = new AdhanKeys();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "new":
                    e = new FavEdit(new ArrayList<>(Arrays.asList(f[3].split(","))), Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                    out.append("-\n");
                    break;
                case "ok": out.append(state(e.ok(Long.parseLong(f[1])), e)).append('\n'); break;
                case "back": out.append(state(e.back(Long.parseLong(f[1])), e)).append('\n'); break;
                case "undo": out.append(state(e.undo(Long.parseLong(f[1])), e)).append('\n'); break;
                case "down": out.append(state(e.down(Long.parseLong(f[1])), e)).append('\n'); break;
                case "move": out.append(state(e.move(Integer.parseInt(f[2]), Long.parseLong(f[1])), e)).append('\n'); break;
                case "up": out.append(state(e.up(Integer.parseInt(f[2]), Long.parseLong(f[1])), e)).append('\n'); break;
                case "key": out.append(state(e.move(FavEdit.step(Integer.parseInt(f[2]), "1".equals(f[3])), Long.parseLong(f[1])), e)).append('\n'); break;
                case "expire": out.append(e.expire(Long.parseLong(f[1])) ? "expired" : "kept").append('|').append(e.undoAvailable(Long.parseLong(f[1]))).append('\n'); break;
                case "step": out.append(FavEdit.step(Integer.parseInt(f[1]), "1".equals(f[2]))).append('\n'); break;
                case "repeat": out.append(FavEdit.stepAllowed(Integer.parseInt(f[1]), Long.parseLong(f[2]), Long.parseLong(f[3]), Long.parseLong(f[4])) ? "step" : "skip").append('\n'); break;
                case "once": out.append(FavEdit.oncePerPress(Integer.parseInt(f[1])) ? "act" : "skip").append('\n'); break;
                case "gate": {   // entering the mode asks the settings' question: ParentalPolicy.MENU_ARRANGE, the «قفل الإعدادات» target
                    ParentalPolicy.State s = ParentalPolicy.stateFrom("1".equals(f[1]), false, f[2], "");
                    ParentalPolicy.Session session = new ParentalPolicy.Session();
                    if ("1".equals(f[3])) session.unlock(ParentalPolicy.targets(ParentalPolicy.MENU_ARRANGE, null));
                    out.append(ParentalPolicy.needsPin(s, session, ParentalPolicy.targets(ParentalPolicy.MENU_ARRANGE, null)) ? "ASK" : "GO").append('\n');
                    break;
                }
                case "adhanreset": keys = new AdhanKeys(); out.append("-\n"); break;
                case "adhan": {   // adhan playing, «إيقاف الأذان بأي زر» on, no bar: what the home screen's first question says to a key
                    int r = keys.decide(Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), true, false, false, true);
                    out.append(r == AdhanKeys.STOP ? "STOP" : r == AdhanKeys.EAT ? "EAT" : "PASS").append('\n');
                    break;
                }
                default: out.append("?\n");
            }
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
