package sa.urtv.home.phone;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.FileInputStream;

/**
 * The «update in progress» record (UpdateRecords) as a small file of the launcher's own. Written to a temporary file, flushed to disk and
 * renamed, so a reader finds the old record or the new one, never half of one.
 */
public final class UpdateStore implements UpdateRecords.Store {
    private final File file, tmp;

    public UpdateStore(Context c) {
        file = new File(c.getFilesDir(), "update-in-progress.txt");
        tmp = new File(c.getFilesDir(), "update-in-progress.tmp");
    }

    @Override public String read() {
        if (!file.isFile()) return null;
        try {
            InputStream in = new FileInputStream(file);
            try {
                byte[] buf = new byte[256];
                int n = 0, r;
                while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
                return new String(buf, 0, n, "UTF-8");
            } finally {
                in.close();
            }
        } catch (java.io.IOException e) {
            return "?";   // there, but unreadable: not a success (UpdateRecords.peek)
        }
    }

    @Override public boolean write(String text) {
        try {
            FileOutputStream o = new FileOutputStream(tmp);
            try {
                o.write(text.getBytes("UTF-8"));
                o.getFD().sync();
            } finally {
                o.close();
            }
            return tmp.renameTo(file);
        } catch (java.io.IOException e) {
            return false;
        }
    }

    @Override public void clear() {
        for (File f : new File[] {file, tmp}) f.delete();
    }
}
