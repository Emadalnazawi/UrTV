package sa.urtv.home;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/** For tests/pin_store_test.py: PinStore and PinCrypto on tests/pin_store_cases.tsv, one answer line per case line. A PinStore lives from one "new" line to the next.
 *  ops (tab separated, then the expected answer): new | set PIN | clear | verify PIN NOW | isset | fails | wait NOW | plain PIN (does any kept value contain it?) | peek (the kept
 *  keys) | corrupt (a broken lock value) | clock NOW UNTIL (a lock that ends at UNTIL, asked at NOW) | same PIN PIN2 (two records of one PIN differ) */
public final class PinStoreRun {
    public static void main(String[] args) throws IOException {
        StringBuilder out = new StringBuilder();
        Map<String, String> kept = new HashMap<>();
        PinStore.Backing backing = new PinStore.Backing() {
            @Override public String get(String key) { return kept.get(key); }
            @Override public void put(String key, String value) { kept.put(key, value); }
            @Override public void remove(String key) { kept.remove(key); }
        };
        PinStore store = new PinStore(backing);
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "new": kept.clear(); store = new PinStore(backing); out.append("-\n"); break;
                case "set": store.set(f[1]); out.append("ok\n"); break;
                case "clear": store.clear(); out.append("ok\n"); break;
                case "isset": out.append(store.isSet()).append('\n'); break;
                case "fails": out.append(store.fails()).append('\n'); break;
                case "wait": out.append(store.waitLeft(Long.parseLong(f[1]))).append('\n'); break;
                case "verify": {
                    PinStore.Result r = store.verify(f[1], Long.parseLong(f[2]));
                    out.append(r.kind == PinStore.OK ? "OK" : r.kind == PinStore.NOT_SET ? "NOT_SET"
                            : r.kind == PinStore.LOCKED ? "LOCKED wait=" + r.waitMs : "WRONG left=" + r.triesLeft + " wait=" + r.waitMs).append('\n');
                    break;
                }
                case "plain": {
                    boolean found = false;
                    for (String v : kept.values()) found |= v.contains(f[1]);
                    out.append(found).append('\n');
                    break;
                }
                case "peek": out.append(String.join(",", new java.util.TreeSet<>(kept.keySet()))).append('\n'); break;
                case "corrupt": kept.put("pinlock", "x,y"); out.append("ok\n"); break;
                case "clock": kept.put("pinlock", "5," + f[2]); out.append(store.waitLeft(Long.parseLong(f[1]))).append('\n'); break;
                case "same": {
                    String a = PinCrypto.create(f[1]), b = PinCrypto.create(f[1]);
                    out.append(!a.equals(b) && PinCrypto.verify(f[1], a) && PinCrypto.verify(f[1], b) && !PinCrypto.verify(f[2], a)).append('\n');
                    break;
                }
                default: out.append("?\n");
            }
        }
        System.out.write(out.toString().getBytes(StandardCharsets.UTF_8));
        System.out.flush();
    }
}
