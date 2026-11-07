package sa.urtv.home.phone;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * The one-time way in. The TV shows a token (128 random bits, in the QR code) and, under it, a 6-digit code for typing; both belong to the same
 * offer, last two minutes and work once: using either spends the offer. Anything that is not the current token or code counts as a wrong try
 * (so does one that arrived late); after five wrong tries in all, nothing is accepted again and the server is stopped by its owner.
 * Times are given in, in milliseconds, so a test can move the clock. No Android in it.
 */
public final class Pairing {
    public static final long TTL_MS = 120_000;
    public static final int MAX_WRONG = 5;

    public enum Result { OK, WRONG, LOCKED }

    /** What the TV shows. */
    public static final class Offer {
        public final String token, code;
        public final long expiresAt;
        Offer(String token, String code, long expiresAt) { this.token = token; this.code = code; this.expiresAt = expiresAt; }
    }

    private final SecureRandom rnd;
    private String token, code;
    private long expiresAt;
    private int wrong;

    public Pairing(SecureRandom rnd) { this.rnd = rnd; }

    /** A fresh offer; the one before it stops working (it does not count as a wrong try). Null once locked. */
    public synchronized Offer offer(long now) {
        if (wrong >= MAX_WRONG) return null;
        byte[] b = new byte[16];
        rnd.nextBytes(b);
        StringBuilder h = new StringBuilder();
        for (byte x : b) h.append(String.format(java.util.Locale.ROOT, "%02x", x & 0xff));
        token = h.toString();
        code = String.format(java.util.Locale.ROOT, "%06d", rnd.nextInt(1_000_000));
        expiresAt = now + TTL_MS;
        return new Offer(token, code, expiresAt);
    }

    /** The offer on show, if it is still good; null when it ran out, was spent or the pairing is locked. */
    public synchronized Offer current(long now) {
        if (token == null || now >= expiresAt || wrong >= MAX_WRONG) return null;
        return new Offer(token, code, expiresAt);
    }

    public synchronized boolean locked() { return wrong >= MAX_WRONG; }

    public synchronized int wrongTries() { return wrong; }

    /** A token (isCode false) or a code (true) from the phone. OK spends the offer. */
    public synchronized Result redeem(String given, boolean isCode, long now) {
        if (wrong >= MAX_WRONG) return Result.LOCKED;
        String want = isCode ? code : token;
        boolean good = want != null && now < expiresAt && same(want, given);
        if (good) {
            token = null;
            code = null;
            return Result.OK;
        }
        wrong++;
        return wrong >= MAX_WRONG ? Result.LOCKED : Result.WRONG;
    }

    /** Spends the offer without a phone (the screen with the QR code was left). */
    public synchronized void withdraw() {
        token = null;
        code = null;
    }

    /** Constant-time comparison. */
    static boolean same(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
