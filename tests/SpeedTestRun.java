package sa.urtv.home;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * For tests/control_center_test.py: SpeedTest against a server of its own on this machine (nothing leaves it). Prints «ok NAME» or «FAIL NAME».
 */
public final class SpeedTestRun {
    private static int failed;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "ok " : "FAIL ") + name);
        if (!ok) failed++;
    }

    public static void main(String[] args) throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        srv.createContext("/fast", ex -> {
            byte[] body = new byte[SpeedTest.BYTES];
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(body); }
        });
        srv.createContext("/bad", ex -> { ex.sendResponseHeaders(500, -1); ex.close(); });
        srv.createContext("/slow", ex -> {   // a few bytes, then silence: only cancel() ends it before the read timeout
            ex.sendResponseHeaders(200, 0);
            try (OutputStream o = ex.getResponseBody()) {
                o.write(new byte[1024]);
                o.flush();
                Thread.sleep(20_000);
            } catch (Exception ignored) { }
        });
        srv.start();
        String base = "http://127.0.0.1:" + srv.getAddress().getPort();
        try {
            check("the host is one constant, https, and the size is a few MB",
                    SpeedTest.url().equals("https://" + SpeedTest.HOST + "/__down?bytes=" + SpeedTest.BYTES) && SpeedTest.BYTES >= 1_000_000 && SpeedTest.BYTES <= 20_000_000);
            double mbps = new SpeedTest().downloadMbps(base + "/fast");
            check("a download gives a positive speed", mbps > 0 && !Double.isInfinite(mbps));
            try { new SpeedTest().downloadMbps(base + "/bad"); check("an HTTP error is an IOException", false); }
            catch (IOException e) { check("an HTTP error is an IOException", e.getMessage().contains("500")); }
            SpeedTest before = new SpeedTest();
            before.cancel();
            try { before.downloadMbps(base + "/fast"); check("cancelled before it starts: nothing is requested", false); }
            catch (IOException e) { check("cancelled before it starts: nothing is requested", before.cancelled()); }
            SpeedTest running = new SpeedTest();
            long[] took = new long[1];
            Thread t = new Thread(() -> {
                long start = System.nanoTime();
                try { running.downloadMbps(base + "/slow"); } catch (IOException ignored) { }
                took[0] = (System.nanoTime() - start) / 1_000_000;
            });
            t.start();
            Thread.sleep(600);
            running.cancel();
            t.join(3000);
            check("cancel() ends a running read at once (well before the 5 s read timeout)", !t.isAlive() && took[0] < 3000 && running.cancelled());
            running.cancel();
            check("cancel() twice is harmless", true);
        } finally {
            srv.stop(0);
        }
        System.exit(failed == 0 ? 0 : 1);
    }
}
