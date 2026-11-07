package sa.urtv.screensaver;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** For tests/focus_key_test.py: FocusKey.java on a plain JVM. Prints PASS/FAIL lines. */
public final class FocusKeyRun {
    static int failed;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  -> " + detail));
        if (!ok) failed++;
    }

    public static void main(String[] args) {
        check("key: a count in the name does not change the row", FocusKey.key("الفرق المتابعة (٢/٢٠)").equals(FocusKey.key("الفرق المتابعة (٣/٢٠)"))
                && FocusKey.key("الأسواق (3/4)").equals("الأسواق"), FocusKey.key("الفرق المتابعة (٢/٢٠)"));
        check("key: an adjustment row keeps its prayer, whatever its minutes", FocusKey.key("الفجر +٢ د").equals(FocusKey.key("الفجر −٣ د"))
                && !FocusKey.key("الفجر +٢ د").equals(FocusKey.key("العصر +٢ د")), FocusKey.key("الفجر +٢ د"));
        check("key: names without numbers are themselves", FocusKey.key("طريقة الحساب").equals("طريقة الحساب") && FocusKey.key(null).isEmpty(), "");
        List<String> rows = Arrays.asList("معاينة الشاشة", "المدينة", "طريقة الحساب", "تعديل وقت الصلاة", "الفجر د", "الظهر د");
        check("pick: the changed row by its key, wherever it now is", FocusKey.pick(rows, "طريقة الحساب", 0) == 2 && FocusKey.pick(rows, "الظهر د", 1) == 5, "");
        List<String> closed = Arrays.asList("معاينة الشاشة", "المدينة", "طريقة الحساب", "تعديل وقت الصلاة", "شريط السنة");
        check("pick: a row that went (its group closed) gives the row now at its place", FocusKey.pick(closed, "الظهر د", 4) == 4, "");
        check("pick: past the end, the last row; nothing to focus, -1", FocusKey.pick(closed, "gone", 40) == 4
                && FocusKey.pick(Collections.<String>emptyList(), "x", 0) == -1, "");
        System.exit(failed == 0 ? 0 : 1);
    }
}
