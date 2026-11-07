package sa.urtv.home.tilawah;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Puts a new file where an old one is without ever leaving the place empty on a failure, with no Android in it (tests/SafeReplaceRun.java
 * runs it with real files and a rename that fails on purpose). The new one is made whole beside the old (a copy, or a rename when it is
 * already in the same folder), then renamed over it; if the system will not rename over a file, the old one is set aside and put back
 * should the next step fail. The old file is only deleted once the new one is in place.
 */
final class SafeReplace {
    /** the rename, so a test can make it fail */
    interface Mover { boolean rename(File from, File to); }

    static final Mover FILES = File::renameTo;

    private SafeReplace() { }

    /** "" when dest now holds src's bytes, else why not; then dest is what it was before. src is left as it is (or moved, when it was renamed). */
    static String replace(File src, File dest, boolean move, Mover mover) {
        File dir = dest.getAbsoluteFile().getParentFile();
        File fresh = new File(dir, dest.getName() + ".new");
        File old = new File(dir, dest.getName() + ".old");
        fresh.delete();
        if (move && src.getAbsoluteFile().getParentFile().equals(dir)) {
            if (!mover.rename(src, fresh)) return "no room to keep it";
        } else {
            String why = copy(src, fresh);
            if (!why.isEmpty()) return why;
        }
        // the usual case: a rename over the old one replaces it in one step
        if (mover.rename(fresh, dest)) return "";
        // else set the old one aside, put the new one in, and undo it all if that fails
        if (dest.exists()) {
            old.delete();
            if (!mover.rename(dest, old)) {
                fresh.delete();
                return "no room to keep it";
            }
        }
        if (!mover.rename(fresh, dest)) {
            if (old.exists()) mover.rename(old, dest);
            fresh.delete();
            return "no room to keep it";
        }
        old.delete();
        return "";
    }

    private static String copy(File src, File dest) {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return "";
        } catch (IOException e) {
            dest.delete();
            return "cannot copy it (" + e.getMessage() + ")";
        }
    }
}
