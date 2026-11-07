package sa.urtv.home;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** For tests/parental_policy_test.py: ParentalPolicy on tests/parental_policy_cases.tsv, one answer line per case line (a state and its session live from one "state" line to the next).
 *  state PINSET KIDS LOCKED ALLOWED (the kept values, as ParentalPolicy.stateFrom reads them) | ask PATH PKG (ASK or GO) | unlock PATH PKG | clear | targets PATH PKG
 *  | visible PKGS (the ones kids mode shows) | kidsstart | kidsleave | nothing (the state of a TV where nothing was ever set) */
public final class ParentalPolicyRun {
    public static void main(String[] args) throws Exception {
        StringBuilder out = new StringBuilder();
        ParentalPolicy.State state = ParentalPolicy.State.nothingSet();
        ParentalPolicy.Session session = new ParentalPolicy.Session();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "state":
                    state = ParentalPolicy.stateFrom("1".equals(f[1]), "1".equals(f[2]), f[3], f[4]);
                    session = new ParentalPolicy.Session();
                    out.append("-\n");
                    break;
                case "nothing":
                    state = ParentalPolicy.stateFrom(false, false, "", "");
                    session = new ParentalPolicy.Session();
                    out.append("-\n");
                    break;
                case "ask":
                    out.append(ParentalPolicy.needsPin(state, session, ParentalPolicy.targets(path(f[1]), f[2].isEmpty() ? null : f[2])) ? "ASK" : "GO").append('\n');
                    break;
                case "unlock":
                    session.unlock(ParentalPolicy.targets(path(f[1]), f[2].isEmpty() ? null : f[2]));
                    out.append("-\n");
                    break;
                case "clear":
                    session.clear();
                    out.append("-\n");
                    break;
                case "targets":
                    out.append(String.join(",", ParentalPolicy.targets(path(f[1]), f[2].isEmpty() ? null : f[2]))).append('\n');
                    break;
                case "visible": {
                    List<String> shown = ParentalPolicy.visible(state, new ArrayList<>(Arrays.asList(f[1].split(","))));
                    out.append(shown.isEmpty() ? "-" : String.join(",", shown)).append('\n');
                    break;
                }
                case "validtarget": out.append(ParentalPolicy.validTarget(f[1].isEmpty() ? null : f[1])).append('\n'); break;
                case "lockedline": out.append(ParentalPolicy.lockedLine(state)).append('\n'); break;
                case "kidsstart": out.append(ParentalPolicy.canStartKids(state)).append('\n'); break;
                case "kidsleave": out.append(ParentalPolicy.leavingKidsNeedsPin(state)).append('\n'); break;
                case "paths": {   // every launch path the policy knows, so the test can compare it with the home screen's
                    List<String> names = new ArrayList<>();
                    for (java.lang.reflect.Field fl : ParentalPolicy.class.getDeclaredFields()) {
                        if (fl.getType() == int.class && java.lang.reflect.Modifier.isStatic(fl.getModifiers())) names.add(fl.getName());
                    }
                    java.util.Collections.sort(names);
                    out.append(String.join(",", names)).append('\n');
                    break;
                }
                default: out.append("?\n");
            }
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }

    private static int path(String name) throws Exception {
        return ParentalPolicy.class.getDeclaredField(name).getInt(null);
    }
}
