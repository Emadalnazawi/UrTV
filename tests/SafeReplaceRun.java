package sa.urtv.home.tilawah;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** For tests/own_adhan_keep_test.py: SafeReplace.java on real files, with a rename that fails at the step asked for. One line per check: name TAB ok. */
public final class SafeReplaceRun {
    static void check(String name, boolean ok) { System.out.println(name + "\t" + (ok ? "ok" : "FAIL")); }

    static String read(File f) throws Exception { return f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "<none>"; }
    static void write(File f, String s) throws Exception { Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8)); }

    /** a rename that fails on the calls listed (1-based): 1 src->new, 2 new->dest, 3 dest->old, 4 new->dest again, 5 old->dest (the undo) */
    static SafeReplace.Mover failing(int... which) {
        int[] calls = {0};
        return (a, b) -> {
            int n = ++calls[0];
            for (int w : which) if (w == n) return false;
            return a.renameTo(b);
        };
    }

    static boolean noLeftovers(File dir) {
        String[] names = dir.list();
        for (String s : names) if (s.endsWith(".new") || s.endsWith(".old")) return false;
        return true;
    }

    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("ownadhan").toFile();
        File dest = new File(dir, "adhan-own.m4a");
        File in = new File(dir, "adhan-own-in.tmp");
        File elsewhere = new File(Files.createTempDirectory("ownsrc").toFile(), "mine.m4a");

        write(dest, "OLD");
        write(in, "NEW");
        check("a moved recording replaces the old", SafeReplace.replace(in, dest, true, SafeReplace.FILES).isEmpty() && read(dest).equals("NEW") && noLeftovers(dir));

        write(elsewhere, "COPY");
        check("a copied recording replaces the old and the source stays", SafeReplace.replace(elsewhere, dest, false, SafeReplace.FILES).isEmpty()
                && read(dest).equals("COPY") && read(elsewhere).equals("COPY") && noLeftovers(dir));

        File gone = new File(dir, "missing.m4a");
        String why = SafeReplace.replace(gone, dest, false, SafeReplace.FILES);
        check("a source that cannot be read: refused, the old one still there", !why.isEmpty() && read(dest).equals("COPY") && noLeftovers(dir));

        write(in, "NEW2");
        why = SafeReplace.replace(in, dest, true, failing(1));
        check("the first rename fails (src into place): refused, old kept", !why.isEmpty() && read(dest).equals("COPY") && noLeftovers(dir));

        write(in, "NEW3");
        why = SafeReplace.replace(in, dest, true, failing(2));   // rename over the old fails: set aside, put in
        check("a system that will not rename over a file: set aside, put in, done", why.isEmpty() && read(dest).equals("NEW3") && noLeftovers(dir));

        write(in, "NEW4");
        why = SafeReplace.replace(in, dest, true, failing(2, 3));   // and the old cannot be set aside
        check("the old cannot be set aside: refused, old kept", !why.isEmpty() && read(dest).equals("NEW3") && noLeftovers(dir));

        write(in, "NEW5");
        why = SafeReplace.replace(in, dest, true, failing(2, 4));   // the old was set aside, the new cannot go in
        check("the new one cannot go in after the old was set aside: the old is put back", !why.isEmpty() && read(dest).equals("NEW3") && noLeftovers(dir));
    }
}
