package sa.urtv.home.phone;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The checks every request goes through before anything else: which network address to listen on, who may connect (private networks only),
 * that the Host and Origin headers name this server and nothing else (a web page on the internet can't reach it by pointing its own name at
 * the TV's address), and how often one client may ask. No Android in it.
 */
public final class Guard {
    private Guard() { }

    /** A network interface as the address search sees it. */
    public static final class Candidate {
        public final String name;
        public final byte[] ipv4;
        public final boolean up;
        public Candidate(String name, byte[] ipv4, boolean up) { this.name = name; this.ipv4 = ipv4; this.up = up; }
    }

    /** The Wi-Fi interface's private IPv4 address among the candidates (names wlan*), else null. Never a wildcard, never a mobile or wired link. */
    public static byte[] wifiAddress(List<Candidate> all) {
        for (Candidate c : all) {
            if (c.up && c.name != null && c.name.toLowerCase(Locale.ROOT).startsWith("wlan") && c.ipv4 != null && c.ipv4.length == 4 && privateV4(c.ipv4)) return c.ipv4;
        }
        return null;
    }

    /** 10/8, 172.16/12 and 192.168/16: the ranges set aside for networks at home. */
    static boolean privateV4(byte[] a) {
        if (a == null || a.length != 4) return false;
        int b0 = a[0] & 0xff, b1 = a[1] & 0xff;
        return b0 == 10 || (b0 == 172 && b1 >= 16 && b1 <= 31) || (b0 == 192 && b1 == 168);
    }

    /** May this client talk to us at all? Only private IPv4 addresses (an IPv4-mapped IPv6 one arrives as IPv4). */
    public static boolean privateClient(InetAddress a) {
        return a instanceof Inet4Address && privateV4(a.getAddress());
    }

    /** "a.b.c.d" for the 4 bytes. */
    public static String text(byte[] a) {
        return (a[0] & 0xff) + "." + (a[1] & 0xff) + "." + (a[2] & 0xff) + "." + (a[3] & 0xff);
    }

    /** The Host header is exactly this server's address and port. */
    public static boolean hostOk(String host, String ip, int port) {
        return host != null && host.equalsIgnoreCase(ip + ":" + port);
    }

    /** The Origin header is exactly this server's own origin. A missing one, "null" or any other site is refused. */
    public static boolean originOk(String origin, String ip, int port) {
        return origin != null && origin.equalsIgnoreCase("http://" + ip + ":" + port);
    }

    /** A browser that says the request comes from another site (Sec-Fetch-Site) is refused; a client that doesn't say is judged by Origin. */
    public static boolean fetchSiteOk(String site) {
        return site == null || site.equals("same-origin") || site.equals("none");
    }

    /** At most `limit` requests per `windowMs` for each client; remembers at most 64 clients. */
    public static final class Rate {
        private final int limit;
        private final long windowMs;
        private final Map<String, List<Long>> seen = new HashMap<>();

        public Rate(int limit, long windowMs) { this.limit = limit; this.windowMs = windowMs; }

        public synchronized boolean allow(String client, long now) {
            if (seen.size() > 64 && !seen.containsKey(client)) {
                for (java.util.Iterator<List<Long>> it = seen.values().iterator(); it.hasNext(); ) {
                    List<Long> l = it.next();
                    if (l.isEmpty() || now - l.get(l.size() - 1) >= windowMs) it.remove();
                }
                if (seen.size() > 64) return false;
            }
            List<Long> l = seen.get(client);
            if (l == null) { l = new ArrayList<>(); seen.put(client, l); }
            while (!l.isEmpty() && now - l.get(0) >= windowMs) l.remove(0);
            if (l.size() >= limit) return false;
            l.add(now);
            return true;
        }
    }
}
