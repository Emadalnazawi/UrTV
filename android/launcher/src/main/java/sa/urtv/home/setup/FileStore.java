package sa.urtv.home.setup;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The setup's ledger and journals as small files in the app's private folder (files/setup/): disabled-by-us.txt, home-switch.json,
 * screensaver-before.txt and install-appop.txt, the names optimize/run uses on a computer. Written all or nothing.
 */
final class FileStore implements SetupEngine.Store {
    private final File dir;

    FileStore(Context c) {
        dir = new File(c.getFilesDir(), "setup");
        dir.mkdirs();
    }

    @Override public synchronized String get(String name) {
        File f = file(name);
        if (!f.isFile()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toString("UTF-8");
        } catch (IOException e) {
            return null;
        }
    }

    @Override public synchronized void put(String name, String text) throws IOException {
        File f = file(name), tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!tmp.renameTo(f)) throw new IOException("could not save " + name);
    }

    private File file(String name) {
        if (!name.matches("[a-z0-9.-]+")) throw new IllegalArgumentException(name);
        return new File(dir, name);
    }
}
