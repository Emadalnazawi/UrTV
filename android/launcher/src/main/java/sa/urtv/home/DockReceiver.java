package sa.urtv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The favourites row from ADB, for agents and scripts. Only the shell can send these: the manifest requires
 * android.permission.DUMP, which apps can't hold.
 *
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.GET_DOCK
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.LIST_APPS
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.SET_DOCK --es apps pkg1,pkg2,...
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.RESET_DOCK
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.HIDDEN                 left out, and why
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.HIDE --es apps pkg1,pkg2,...
 *   am broadcast -n sa.urtv.home/.DockReceiver -a sa.urtv.home.SHOW --es apps pkg1,pkg2,...
 *
 * The answer is in the "data" of am's output. optimize/dock.sh wraps them all. People use «ترتيب المفضلة»
 * (DockActivity) instead.
 */
public class DockReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String action = String.valueOf(i.getAction());
        Map<String, String> apps = Dock.launchable(c);
        switch (action) {
            case "sa.urtv.home.LIST_APPS": {
                StringBuilder b = new StringBuilder();
                for (Map.Entry<String, String> e : apps.entrySet()) b.append(e.getKey()).append(" | ").append(e.getValue()).append('\n');
                setResultData(b.toString().trim());
                return;
            }
            case "sa.urtv.home.GET_DOCK":
                setResultData("dock: " + String.join(",", Dock.shown(c, apps)));
                return;
            case "sa.urtv.home.RESET_DOCK":
                Dock.reset(c);
                setResultData("dock: " + String.join(",", Dock.shown(c, apps)) + " (defaults)");
                return;
            case "sa.urtv.home.SET_DOCK": {
                String raw = i.getStringExtra("apps");
                if (raw == null) { setResultData("error: add --es apps pkg1,pkg2,..."); return; }
                List<String> row = new ArrayList<>(), skipped = new ArrayList<>();
                for (String p : raw.split(",")) {
                    p = p.trim();
                    if (p.isEmpty() || row.contains(p)) continue;
                    if (apps.containsKey(p) && row.size() < Dock.MAX) row.add(p); else skipped.add(p);
                }
                if (row.isEmpty()) { setResultData("error: none of these apps are on this TV's home screen"); return; }
                Dock.save(c, row);
                setResultData("dock: " + String.join(",", Dock.shown(c, apps))
                    + (skipped.isEmpty() ? "" : "  skipped (not installed, hidden or over " + Dock.MAX + "): "
                       + String.join(",", skipped)));
                return;
            }
            case "sa.urtv.home.HIDDEN": {
                StringBuilder b = new StringBuilder();
                for (Map.Entry<String, String> e : Dock.leftOut(c).entrySet()) b.append(e.getKey()).append(" | ").append(e.getValue()).append('\n');
                setResultData(b.length() == 0 ? "nothing left out" : b.toString().trim());
                return;
            }
            case "sa.urtv.home.HIDE":
            case "sa.urtv.home.SHOW": {
                String raw = i.getStringExtra("apps");
                if (raw == null) { setResultData("error: add --es apps pkg1,pkg2,..."); return; }
                List<String> pkgs = new ArrayList<>();
                for (String p : raw.split(",")) if (!p.trim().isEmpty()) pkgs.add(p.trim());
                boolean hide = action.endsWith("HIDE");
                Dock.hideOrShow(c, pkgs, hide);
                setResultData((hide ? "hidden: " : "shown: ") + String.join(",", pkgs));
                return;
            }
            case "sa.urtv.home.EPG":
                setResultData(epg(c) + "\n" + liveness(c));
                return;
            default:
                setResultData("error: unknown action " + action);
        }
    }

    /** What the apps publish to the TV's program database (channels, their programs, «watch next»), per app. */
    private static String epg(Context c) {
        StringBuilder b = new StringBuilder();
        String[][] tables = {
            {"channel", "package_name", "display_name"},
            {"preview_program", "package_name", "title"},
            {"watch_next_program", "package_name", "title"},
            {"program", "package_name", "title"},
        };
        for (String[] t : tables) {
            b.append("== ").append(t[0]).append('\n');
            try (android.database.Cursor cur = c.getContentResolver().query(
                    android.net.Uri.parse("content://android.media.tv/" + t[0]), new String[]{t[1], t[2]}, null, null, null)) {
                if (cur == null) { b.append("  (null)\n"); continue; }
                java.util.Map<String, java.util.List<String>> by = new java.util.TreeMap<>();
                while (cur.moveToNext()) {
                    String p = cur.getString(0), n = cur.getString(1);
                    if (!by.containsKey(p)) by.put(p, new java.util.ArrayList<>());
                    if (by.get(p).size() < 6) by.get(p).add(n);
                }
                b.append("  rows: ").append(cur.getCount()).append('\n');
                for (java.util.Map.Entry<String, java.util.List<String>> e : by.entrySet())
                    b.append("  ").append(e.getKey()).append(": ").append(String.join(" | ", e.getValue())).append('\n');
            } catch (RuntimeException e) {
                b.append("  error: ").append(e).append('\n');
            }
        }
        return b.toString().trim();
    }

    /** Per app: how many published items are live, and of which kinds (TvContract type codes). */
    private static String liveness(Context c) {
        StringBuilder b = new StringBuilder("== live/type\n");
        try (android.database.Cursor cur = c.getContentResolver().query(android.net.Uri.parse("content://android.media.tv/preview_program"),
                new String[]{"package_name", "live", "type"}, null, null, null)) {
            java.util.Map<String, int[]> live = new java.util.TreeMap<>();
            java.util.Map<String, java.util.Set<Integer>> types = new java.util.TreeMap<>();
            while (cur != null && cur.moveToNext()) {
                String p = cur.getString(0);
                if (!live.containsKey(p)) { live.put(p, new int[2]); types.put(p, new java.util.TreeSet<>()); }
                live.get(p)[0]++;
                if (cur.getInt(1) == 1) live.get(p)[1]++;
                types.get(p).add(cur.getInt(2));
            }
            for (String p : live.keySet())
                b.append("  ").append(p).append(": ").append(live.get(p)[0]).append(" items, live ").append(live.get(p)[1])
                        .append(", types ").append(types.get(p)).append('\n');
        } catch (RuntimeException e) { b.append("  error: ").append(e).append('\n'); }
        return b.toString().trim();
    }
}
