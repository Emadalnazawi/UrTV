package sa.urtv.home;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** For tests/type_into_test.py: TypeInto (which field, what it holds afterwards, when it is refused) on a fake screen. Prints PASS/FAIL lines. */
public final class TypeIntoRun {
    static int failed;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) failed++;
    }

    static final class N implements TypeInto.Node {
        final String id;
        final boolean editable, focused, password, visible;
        final List<N> kids = new ArrayList<>();
        N(String id, boolean editable, boolean focused, boolean password, boolean visible, N... kids) {
            this.id = id; this.editable = editable; this.focused = focused; this.password = password; this.visible = visible;
            this.kids.addAll(Arrays.asList(kids));
        }
        static N box(String id, N... kids) { return new N(id, false, false, false, true, kids); }
        static N field(String id) { return new N(id, true, false, false, true); }
        static N focusedField(String id) { return new N(id, true, true, false, true); }
        @Override public boolean editable() { return editable; }
        @Override public boolean focused() { return focused; }
        @Override public boolean password() { return password; }
        @Override public boolean visible() { return visible; }
        @Override public List<N> children() { return kids; }
    }

    static String id(TypeInto.Node n) { return n == null ? "none" : ((N) n).id; }

    public static void main(String[] args) {
        check("service off: refused before anything is looked at", TypeInto.allowed(false, "com.app", false, false, false, false) == TypeInto.Refusal.SERVICE_OFF);
        check("service off wins over a lock", TypeInto.allowed(false, "com.app", false, true, false, false) == TypeInto.Refusal.SERVICE_OFF);
        check("a window that does not say its package is refused", TypeInto.allowed(true, null, false, false, false, false) == TypeInto.Refusal.REFUSED
            && TypeInto.allowed(true, "", false, false, false, false) == TypeInto.Refusal.REFUSED);
        check("our own screens are refused (they take the text by their own path)", TypeInto.allowed(true, "sa.urtv.home", true, false, false, false) == TypeInto.Refusal.REFUSED);
        check("a locked app is refused", TypeInto.allowed(true, "com.app", false, true, false, false) == TypeInto.Refusal.LOCKED);
        check("an app kids mode hides is refused", TypeInto.allowed(true, "com.app", false, false, true, false) == TypeInto.Refusal.LOCKED);
        check("the TV's settings or Wi-Fi screen while those are locked is refused", TypeInto.allowed(true, "com.settings", false, false, false, true) == TypeInto.Refusal.LOCKED);
        check("an app that is none of those is allowed", TypeInto.allowed(true, "com.app", false, false, false, false) == TypeInto.Refusal.NONE);

        N screen = N.box("root", N.field("search"), N.box("row", N.focusedField("name"), N.field("other")));
        check("the field with the focus wins over an earlier one", id(TypeInto.pick(screen)).equals("name"));
        check("with no focus, the first editable field in reading order", id(TypeInto.pick(N.box("root", N.box("a", N.field("first")), N.field("second")))).equals("first"));
        check("a field that is not visible is not chosen", id(TypeInto.pick(N.box("root", new N("hidden", true, false, false, false), N.field("shown")))).equals("shown"));
        check("a focused field that is not visible is not chosen either", id(TypeInto.pick(N.box("root", new N("hidden", true, true, false, false), N.field("shown")))).equals("shown"));
        check("a screen with no editable field gives none", TypeInto.pick(N.box("root", N.box("a"), N.box("b"))) == null);
        check("no screen at all gives none", TypeInto.pick(null) == null);
        check("a null child is skipped, not a crash", id(TypeInto.pick(N.box("root", (N) null, N.field("ok")))).equals("ok"));
        check("none is NO_FIELD", TypeInto.fieldProblem(null) == TypeInto.Refusal.NO_FIELD);
        check("a password field is refused", TypeInto.fieldProblem(new N("pw", true, true, true, true)) == TypeInto.Refusal.PASSWORD_FIELD);
        check("an ordinary field is fine", TypeInto.fieldProblem(N.field("x")) == TypeInto.Refusal.NONE);

        check("replace: the typed text alone", "ماء".equals(TypeInto.compose("old", "ماء", false)));
        check("append: after what is there", "oldماء".equals(TypeInto.compose("old", "ماء", true)));
        check("append to an empty or hint-only field (null)", "ماء".equals(TypeInto.compose(null, "ماء", true)) && "ماء".equals(TypeInto.compose("", "ماء", true)));
        check("replace ignores what is there", "x".equals(TypeInto.compose(null, "x", false)));
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < TypeInto.MAX_FIELD; i++) big.append('a');
        check("append past the limit is refused (null), replace of the typed text is not", TypeInto.compose(big.toString(), "b", true) == null && "b".equals(TypeInto.compose(big.toString(), "b", false)));
        check("exactly the limit is fine", TypeInto.compose(big.substring(0, TypeInto.MAX_FIELD - 1), "b", true).length() == TypeInto.MAX_FIELD);
        System.out.println(failed == 0 ? "ALL PASS" : failed + " FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }
}
