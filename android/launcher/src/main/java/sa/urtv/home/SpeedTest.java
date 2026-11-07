package sa.urtv.home;

// Adapted from talkies (https://github.com/rajupp/talkies, MIT, (c) 2026 TechPP) SpeedTest.java, see THIRD_PARTY_NOTICES.md.
// Adapted from Matinee (app/src/main/java/com/tcltv/sahne/system/SpeedTest.kt), MIT License,
// Copyright (c) 2026 Yiğit Can Polat. See THIRD_PARTY_NOTICES.md.

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * «اختبار السرعة»: download speed from Cloudflare's public speed-test endpoint, a bounded read of a few MB. Nothing is uploaded and the
 * request carries nothing but what any HTTPS request does. Blocking: call it off the main thread (ControlCenter does), and cancel() from
 * any thread stops it at once (the panel closed). No Android in it (tests/SpeedTestRun.java runs it against a local server).
 */
final class SpeedTest {
    /** The one place the host is named. */
    static final String HOST = "speed.cloudflare.com";
    /** how many bytes the test asks for: a few MB, so a slow line is not held for long and a fast one finishes early */
    static final int BYTES = 6_000_000;
    static final long MEASURE_NANOS = 4_000_000_000L;
    static final int TIMEOUT_MS = 5000;
    /** the waiting thread looks at cancel() this often */
    static final int POLL_MS = 250;

    private volatile boolean cancelled;
    private volatile HttpURLConnection live;

    /** The thing to ask for: https, the host above, a bounded size. */
    static String url() { return "https://" + HOST + "/__down?bytes=" + BYTES; }

    /** Stop now: the read in progress fails, and run() ends with a cancelled result. Safe to call at any time, more than once. */
    void cancel() {
        cancelled = true;
        HttpURLConnection c = live;
        if (c == null) return;
        // disconnect() can wait for a read that is blocked: never on the caller's thread (it may be the main one)
        Thread t = new Thread(c::disconnect, "speed-cancel");
        t.setDaemon(true);
        t.start();
    }

    boolean cancelled() { return cancelled; }

    /** Megabits per second from the default URL. */
    double downloadMbps() throws IOException { return downloadMbps(url()); }

    /**
     * Megabits per second from a URL (the test's own server uses this). Throws IOException when it fails, and when it was cancelled. The
     * download runs on a thread of its own and this thread only waits, looking at cancel() every POLL_MS: closing a connection under a
     * blocked read is not reliable on every Android, so a cancelled test returns at once and the reader ends by itself (disconnect, or its timeout).
     */
    double downloadMbps(String address) throws IOException {
        if (cancelled) throw new IOException("cancelled");
        final Object[] result = new Object[1];   // a Double, or an IOException
        final CountDownLatch done = new CountDownLatch(1);
        Thread reader = new Thread(() -> {
            try { result[0] = read(address); } catch (IOException | RuntimeException e) { result[0] = e instanceof IOException ? e : new IOException(e); }
            done.countDown();
        }, "speed-read");
        reader.setDaemon(true);
        reader.start();
        try {
            while (!done.await(POLL_MS, TimeUnit.MILLISECONDS)) {
                if (cancelled) break;
            }
        } catch (InterruptedException e) {
            cancel();
        }
        if (cancelled) {
            cancel();   // the disconnect, off this thread
            throw new IOException("cancelled");
        }
        if (result[0] instanceof IOException) throw (IOException) result[0];
        return (Double) result[0];
    }

    private double read(String address) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        live = c;
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setUseCaches(false);
            if (cancelled) throw new IOException("cancelled");
            if (c.getResponseCode() / 100 != 2) throw new IOException("HTTP " + c.getResponseCode());
            byte[] buf = new byte[64 * 1024];
            long total = 0, start = System.nanoTime();
            try (InputStream in = c.getInputStream()) {
                while (!cancelled && System.nanoTime() - start < MEASURE_NANOS && total < BYTES) {
                    int n = in.read(buf);
                    if (n < 0) break;
                    total += n;
                }
            }
            if (cancelled) throw new IOException("cancelled");
            double seconds = (System.nanoTime() - start) / 1e9;
            if (seconds <= 0 || total == 0) throw new IOException("No data received");
            return total * 8 / seconds / 1_000_000;
        } finally {
            live = null;
            c.disconnect();
        }
    }
}
