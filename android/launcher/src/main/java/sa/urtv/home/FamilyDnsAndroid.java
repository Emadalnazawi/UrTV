package sa.urtv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;
import android.provider.Settings;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;

/**
 * FamilyDns on a real TV: Settings.Global through the ContentResolver (needs WRITE_SECURE_SETTINGS, granted only over ADB by
 * `optimize/dock.sh familydns grant`), the record of the originals in the launcher's SharedPreferences. Everything here may block
 * (the network): run it through {@link #run}, never on the UI thread.
 */
final class FamilyDnsAndroid implements FamilyDns.Ports {
    private static final String K_HAS = "fdns_has", K_MODE = "fdns_mode", K_MODE_NULL = "fdns_mode_null",
        K_SPEC = "fdns_spec", K_SPEC_NULL = "fdns_spec_null", K_LAST = "fdns_last", K_CHECKING = "fdns_checking_since";
    private static boolean recovered;
    private static final String CHECK = "https://connectivitycheck.gstatic.com/generate_204";
    private static Thread worker;

    private final Context c;
    FamilyDnsAndroid(Context c) { this.c = c.getApplicationContext(); }

    /** Once per process (and at boot, SleepBootReceiver): a check cut short by a crash or a reboot puts the originals back. */
    static synchronized void recover(Context c) {
        recovered = true;
        if (busy()) return;
        new FamilyDns(new FamilyDnsAndroid(c)).recover();
    }

    private static synchronized void recoverOnce(Context c) { if (!recovered) recover(c); }

    /** "off": no network call, so it runs where it is called. The result is kept (lastResult). */
    static FamilyDns.Result disableNow(Context c) {
        recoverOnce(c);
        FamilyDns.Result r = new FamilyDns(new FamilyDnsAndroid(c)).disable();
        Dock.prefs(c).edit().putString(K_LAST, r.name()).commit();
        return r;
    }

    static boolean supported(Context c) { return new FamilyDns(new FamilyDnsAndroid(c)).supported(); }

    interface Done { void done(FamilyDns.Result r); }

    /** on or off on a background thread (one at a time); the result is kept (lastResult) and handed to `done` on that thread. */
    static synchronized boolean run(Context c, boolean enable, Done done) {
        if (worker != null && worker.isAlive()) return false;
        final Context app = c.getApplicationContext();
        recoverOnce(app);
        Dock.prefs(app).edit().putString(K_LAST, "RUNNING").commit();   // before the thread starts: a status poll never shows a stale result
        worker = new Thread(() -> {
            FamilyDns f = new FamilyDns(new FamilyDnsAndroid(app));
            FamilyDns.Result r = enable ? f.enable() : f.disable();
            Dock.prefs(app).edit().putString(K_LAST, r.name()).commit();
            if (done != null) done.done(r);
        }, "family-dns");
        worker.start();
        return true;
    }

    static synchronized boolean busy() { return worker != null && worker.isAlive(); }

    /** The last result of run(), or "" if none yet. */
    static String lastResult(Context c) { return Dock.prefs(c).getString(K_LAST, ""); }

    /** Reading is quick and local: fine anywhere. */
    static FamilyDns.Status status(Context c) { recoverOnce(c); return new FamilyDns(new FamilyDnsAndroid(c)).status(); }

    @Override public boolean canWriteSecure() {
        return c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public boolean probe(String host, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);   // the name is resolved here too
            return true;
        } catch (Exception e) { return false; }
    }

    @Override public String getGlobal(String key) {
        try { return Settings.Global.getString(c.getContentResolver(), key); }
        catch (RuntimeException e) { return FamilyDns.UNREADABLE; }
    }

    @Override public boolean putGlobal(String key, String value) {
        try { return Settings.Global.putString(c.getContentResolver(), key, value); }
        catch (RuntimeException e) { return false; }
    }

    /** There is no delete in the public API: putting null removes the value (it reads back as null, "not set"). */
    @Override public boolean deleteGlobal(String key) {
        try { return Settings.Global.putString(c.getContentResolver(), key, null); }
        catch (RuntimeException e) { return false; }
    }

    @Override public int sdkInt() { return Build.VERSION.SDK_INT; }

    /** The active network says Private DNS is active with our host (API 28+). That is only the setting having propagated; lookupFreshName checks it answers. */
    @Override public boolean privateDnsValidated(String host, int ms) {
        if (Build.VERSION.SDK_INT < FamilyDns.MIN_SDK) return false;
        long end = System.currentTimeMillis() + ms;
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            while (System.currentTimeMillis() < end) {
                Network n = cm.getActiveNetwork();   // asked again every poll: the network can change while the setting is taken
                LinkProperties lp = n == null ? null : cm.getLinkProperties(n);   // a fresh copy each time, never a kept one
                if (lp != null && lp.isPrivateDnsActive() && sameHost(host, lp.getPrivateDnsServerName()) && FamilyDns.validatedServersOk(validatedServers(lp))) return true;
                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // no connectivity service: not validated
        }
        return false;
    }

    private static boolean sameHost(String want, String got) {
        if (got == null) return false;
        String g = got.endsWith(".") ? got.substring(0, got.length() - 1) : got;
        return want.equalsIgnoreCase(g);
    }

    /** How many Private DNS servers the TV says it validated, or null when this Android cannot say. */
    private static Integer validatedServers(LinkProperties lp) {
        try {
            Object r = LinkProperties.class.getMethod("getValidatedPrivateDnsServers").invoke(lp);
            return r instanceof java.util.List ? Integer.valueOf(((java.util.List<?>) r).size()) : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * A unique random label under connectivitycheck.gstatic.com: no cache anywhere has it, so the answer (an address, or NXDOMAIN) has to
     * come from the resolver now in use. Runs on a helper thread so the wait is bounded; FamilyDns.resolverAnswers decides what it means.
     */
    @Override public FamilyDns.Lookup lookupFreshName(int waitMs) {
        byte[] b = new byte[8];
        new java.security.SecureRandom().nextBytes(b);
        StringBuilder h = new StringBuilder();
        for (byte x : b) h.append(String.format(java.util.Locale.US, "%02x", x & 0xff));
        final String name = h + ".connectivitycheck.gstatic.com";
        final java.util.concurrent.atomic.AtomicReference<FamilyDns.Lookup.Kind> kind = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        final long start = System.currentTimeMillis();
        Thread t = new Thread(() -> {
            try {
                java.net.InetAddress.getByName(name);
                kind.set(FamilyDns.Lookup.Kind.RESOLVED);
            } catch (java.net.UnknownHostException e) {
                Throwable c = e.getCause();
                // android.system.GaiException is hidden from the SDK apps compile against: recognise it by name and read its public `error` field
                int code = 0;
                boolean gai = c != null && "android.system.GaiException".equals(c.getClass().getName());
                if (gai) {
                    try { code = c.getClass().getField("error").getInt(c); } catch (Exception ignored) { gai = false; }
                }
                FamilyDns.Lookup.Kind k = FamilyDns.unknownHostKind(gai, code,
                        android.system.OsConstants.EAI_NODATA, android.system.OsConstants.EAI_NONAME);
                // Android 14 may hide the `error` field from apps: then the resolver's own text says «no such name»
                if (k == FamilyDns.Lookup.Kind.FAILED && !gai && c != null && "android.system.GaiException".equals(c.getClass().getName())
                        && (FamilyDns.messageIsNoSuchName(c.getMessage()) || FamilyDns.messageIsNoSuchName(e.getMessage()))) k = FamilyDns.Lookup.Kind.NOT_FOUND;
                kind.set(k);
            } catch (Exception e) {
                kind.set(FamilyDns.Lookup.Kind.FAILED);
            }
            done.countDown();
        }, "family-dns-lookup");
        t.setDaemon(true);
        t.start();
        try {
            if (!done.await(waitMs, java.util.concurrent.TimeUnit.MILLISECONDS)) return new FamilyDns.Lookup(FamilyDns.Lookup.Kind.TIMEOUT, waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new FamilyDns.Lookup(FamilyDns.Lookup.Kind.FAILED, System.currentTimeMillis() - start);
        }
        return new FamilyDns.Lookup(kind.get(), System.currentTimeMillis() - start);
    }

    @Override public void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Override public boolean internetWorks(int totalMs) {
        long end = System.currentTimeMillis() + totalMs;
        while (true) {
            long left = end - System.currentTimeMillis();
            if (left <= 0) return false;
            HttpURLConnection h = null;
            try {
                h = (HttpURLConnection) new URL(CHECK).openConnection();
                int t = (int) Math.max(500, Math.min(4000, left));
                h.setConnectTimeout(t);
                h.setReadTimeout(t);
                h.setUseCaches(false);
                h.setRequestProperty("Cache-Control", "no-cache");
                h.setInstanceFollowRedirects(false);
                int code = h.getResponseCode();
                if (code == 204 || code == 200) return true;
            } catch (Exception e) {
                // not yet: try again until the time is up
            } finally {
                if (h != null) h.disconnect();
            }
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
    }

    @Override public long checkingSince() { return Dock.prefs(c).getLong(K_CHECKING, 0); }
    @Override public boolean markChecking() { return Dock.prefs(c).edit().putLong(K_CHECKING, System.currentTimeMillis()).commit(); }
    @Override public void clearChecking() { Dock.prefs(c).edit().remove(K_CHECKING).commit(); }

    @Override public FamilyDns.Record loadRecord() {
        SharedPreferences p = Dock.prefs(c);
        if (!p.getBoolean(K_HAS, false)) return null;
        return new FamilyDns.Record(p.getBoolean(K_MODE_NULL, false) ? null : p.getString(K_MODE, null),
                                    p.getBoolean(K_SPEC_NULL, false) ? null : p.getString(K_SPEC, null));
    }

    @Override public boolean saveRecord(FamilyDns.Record r) {
        return Dock.prefs(c).edit().putBoolean(K_HAS, true)
            .putBoolean(K_MODE_NULL, r.mode == null).putString(K_MODE, r.mode)
            .putBoolean(K_SPEC_NULL, r.specifier == null).putString(K_SPEC, r.specifier).commit();
    }

    @Override public void clearRecord() {
        Dock.prefs(c).edit().remove(K_HAS).remove(K_MODE).remove(K_MODE_NULL).remove(K_SPEC).remove(K_SPEC_NULL).commit();
    }
}
