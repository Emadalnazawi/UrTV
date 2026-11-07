package sa.urtv.home.phone;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * The phones the owner has allowed. A session is a 256-bit random value; it ends 30 minutes after its last use, or 4 hours after it began,
 * whichever is first, and the owner can cancel it. Nothing is kept on disk: a restart of the app ends them all. Times come in as arguments.
 * No Android in it.
 */
public final class Sessions {
    public static final long IDLE_MS = 30 * 60_000L, MAX_MS = 4 * 3_600_000L;
    public static final int MAX_SESSIONS = 5;

    /** What the TV may show about a session (never its value). */
    public static final class Info {
        public final String id, ip;
        public final long since, lastUsed;
        Info(String id, String ip, long since, long lastUsed) { this.id = id; this.ip = ip; this.since = since; this.lastUsed = lastUsed; }
    }

    private static final class S {
        final String id, secret, ip;
        final long since;
        long lastUsed;
        S(String id, String secret, String ip, long since) { this.id = id; this.secret = secret; this.ip = ip; this.since = since; lastUsed = since; }
    }

    private final SecureRandom rnd;
    private final List<S> all = new ArrayList<>();

    public Sessions(SecureRandom rnd) { this.rnd = rnd; }

    private String hex(int bytes) {
        byte[] b = new byte[bytes];
        rnd.nextBytes(b);
        StringBuilder h = new StringBuilder();
        for (byte x : b) h.append(String.format(java.util.Locale.ROOT, "%02x", x & 0xff));
        return h.toString();
    }

    private boolean alive(S s, long now) { return now - s.lastUsed < IDLE_MS && now - s.since < MAX_MS; }

    private void sweep(long now) {
        for (int i = all.size() - 1; i >= 0; i--) if (!alive(all.get(i), now)) all.remove(i);
    }

    /** A new session and its secret (64 hex digits); null when five phones are already connected. */
    public synchronized String issue(String ip, long now) {
        sweep(now);
        if (all.size() >= MAX_SESSIONS) return null;
        S s = new S(hex(4), hex(32), ip, now);
        all.add(s);
        return s.secret;
    }

    /** Does this value belong to a live session? Counts as use. Every session is compared, in constant time, whether or not one matches. */
    public synchronized boolean check(String secret, long now) {
        sweep(now);
        S hit = null;
        for (S s : all) if (Pairing.same(s.secret, secret)) hit = s;
        if (hit == null) return false;
        hit.lastUsed = now;
        return true;
    }

    /**
     * check(), and the request must come from the address the phone was allowed from: the transport is plain HTTP on the home Wi-Fi, so a
     * session value seen in one request by another device on that network is no use from that device's own address.
     */
    public synchronized boolean checkFrom(String secret, String ip, long now) {
        sweep(now);
        S hit = null;
        for (S s : all) if (Pairing.same(s.secret, secret)) hit = s;
        if (hit == null || ip == null || !ip.equals(hit.ip)) return false;
        hit.lastUsed = now;
        return true;
    }

    /** The id (not the secret) of the session this value belongs to, or null. Does not count as use. */
    public synchronized String idOf(String secret, long now) {
        sweep(now);
        String id = null;
        for (S s : all) if (Pairing.same(s.secret, secret)) id = s.id;
        return id;
    }

    public synchronized boolean revoke(String id) {
        for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(id)) { all.remove(i); return true; }
        return false;
    }

    public synchronized int count(long now) {
        sweep(now);
        return all.size();
    }

    public synchronized List<Info> list(long now) {
        sweep(now);
        List<Info> out = new ArrayList<>();
        for (S s : all) out.add(new Info(s.id, s.ip, s.since, s.lastUsed));
        return out;
    }

    public synchronized void clear() { all.clear(); }
}
