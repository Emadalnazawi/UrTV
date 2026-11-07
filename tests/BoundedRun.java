package sa.urtv.home;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/** For tests/bounded_test.py: Bounded.java, one answer per line. */
public class BoundedRun {
    /** a server that never stops */
    static final class Endless extends InputStream {
        long given;
        @Override public int read() { given++; return 7; }
        @Override public int read(byte[] b, int o, int l) { given += l; return l; }
    }

    static String read(InputStream in, int max) {
        try { return "ok " + Bounded.readAll(in, max).length; } catch (IOException e) { return "too long"; }
    }

    public static void main(String[] a) {
        System.out.println(read(new ByteArrayInputStream(new byte[100]), 100));
        System.out.println(read(new ByteArrayInputStream(new byte[101]), 100));
        System.out.println(read(new ByteArrayInputStream(new byte[0]), 100));
        Endless e = new Endless();
        System.out.println(read(e, Bounded.JSON_MAX) + " " + (e.given <= Bounded.JSON_MAX + 16384 ? "stopped" : "ran on " + e.given));
        System.out.println(Bounded.sampleSize(4000, 1920) + " " + Bounded.sampleSize(1920, 1920) + " " + Bounded.sampleSize(8000, 1920) + " " + Bounded.sampleSize(500, 1920)
            + " " + Bounded.sampleSize(3840, 1920) + " " + Bounded.sampleSize(4000, 0));
        System.out.println(Bounded.fitting(new long[] {10, 10, 10}, 25) + " " + Bounded.fitting(new long[] {10, 10, 10}, 30) + " " + Bounded.fitting(new long[] {50, 1}, 40)
            + " " + Bounded.fitting(new long[0], 40));
        System.out.println(Bounded.JSON_MAX + " " + Bounded.IMAGE_MAX + " " + Bounded.CACHE_MAX);
    }
}
