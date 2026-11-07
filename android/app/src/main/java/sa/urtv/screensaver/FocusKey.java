package sa.urtv.screensaver;

import java.util.List;

/**
 * Which settings row gets the focus back after the list is built again (SettingsActivity.render, on every change): the row that had it,
 * known by its name without the numbers in it («الفرق المتابعة (٢/٢٠)» and «الفرق المتابعة (٣/٢٠)» are one row), else the row now at its
 * place (it went: a group closed), else the last one. Plain Java: tests/FocusKeyRun.java runs it.
 */
final class FocusKey {
    private FocusKey() { }

    /** A row's name as its key: digits (Latin and Arabic), signs, brackets and slashes out, spaces collapsed. */
    static String key(String label) {
        if (label == null) return "";
        return label.replaceAll("[0-9٠-٩+\\-−()/٪%]", " ").replaceAll("\\s+", " ").trim();
    }

    /** The index to focus in the rebuilt list: the row with that key, else the one at the old index (or the last), -1 for none. */
    static int pick(List<String> keys, String want, int wasIndex) {
        if (keys.isEmpty()) return -1;
        if (want != null) {
            int at = keys.indexOf(want);
            if (at >= 0) return at;
        }
        return Math.max(0, Math.min(wasIndex, keys.size() - 1));
    }
}
