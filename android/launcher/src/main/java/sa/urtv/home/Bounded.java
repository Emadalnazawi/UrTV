package sa.urtv.home;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Limits for what comes off the network, as plain code so tests/bounded_test.py can run it without Android: no answer is read
 * past its cap (a server that never stops cannot fill the memory or the disk), and a photo's cache has a size, not only a count.
 */
final class Bounded {
    private Bounded() { }

    /** a JSON answer (the photo list, a search) is at most 2 MB, a photo at most 8 MB */
    static final int JSON_MAX = 2 << 20, IMAGE_MAX = 8 << 20;
    /** the hosted photos kept on the device never take more than this, whatever their number */
    static final long CACHE_MAX = 40L << 20;

    /** All of the stream; an IOException as soon as it is longer than max (nothing past max is read). */
    static byte[] readAll(InputStream in, int max) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf, 0, (int) Math.min(buf.length, (long) max + 1 - b.size()))) > 0) {
            b.write(buf, 0, n);
            if (b.size() > max) throw new IOException("answer is longer than " + max + " bytes");
        }
        return b.toByteArray();
    }

    /** The largest power of two that leaves the picture at least wantWidth wide: a 4000 px original is not held whole for a 1920 px screen. */
    static int sampleSize(int width, int wantWidth) {
        int sample = 1;
        while (width / (sample * 2) >= Math.max(1, wantWidth)) sample *= 2;
        return sample;
    }

    /** How many of the files (newest first, their sizes) fit in the budget: the rest are to go. */
    static int fitting(long[] sizesNewestFirst, long budget) {
        long sum = 0;
        int k = 0;
        for (long s : sizesNewestFirst) {
            if (sum + s > budget) break;
            sum += s;
            k++;
        }
        return k;
    }
}
