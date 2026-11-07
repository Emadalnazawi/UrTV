package sa.urtv.home;

import java.util.HashMap;
import java.util.Map;

/** For tests/family_dns_test.py: FamilyDns with a fake TV, one answer per line. */
public class FamilyDnsRun {
    static final class Fake implements FamilyDns.Ports {
        boolean breakRestore = false, failModePut = false;
        boolean grant = true, route = true, internet = true, saveOk = true, ignorePuts = false, validated = true;
        FamilyDns.Lookup.Kind lookup = FamilyDns.Lookup.Kind.NOT_FOUND;
        long lookupMs = 40;
        int lookups;
        int sdk = 29;
        long checking;
        long checkingAtFirstPut = -1;
        final Map<String, String> g = new HashMap<>();
        final java.util.Set<String> unreadable = new java.util.HashSet<>();
        FamilyDns.Record rec;
        int writes;
        public boolean canWriteSecure() { return grant; }
        public boolean probe(String h, int p, int t) { return route && h.equals(FamilyDns.HOST) && p == 853; }
        public String getGlobal(String k) { return unreadable.contains(k) ? FamilyDns.UNREADABLE : g.get(k); }
        public boolean putGlobal(String k, String v) { writes++; if (failModePut && k.equals("private_dns_mode") && v.equals("hostname")) return false; if (breakRestore && !FamilyDns.HOST.equals(v) && !"hostname".equals(v)) return false; if (checkingAtFirstPut < 0) checkingAtFirstPut = checking; if (!ignorePuts) g.put(k, v); return true; }
        public boolean deleteGlobal(String k) { writes++; if (breakRestore) return false; g.remove(k); return true; }
        public int sdkInt() { return sdk; }
        public boolean privateDnsValidated(String h, int ms) { return validated && h.equals(FamilyDns.HOST) && ms == FamilyDns.VALIDATE_MS; }
        int failFirst;   // the first N lookups fail fast (the resolver is still validating), then `lookup` applies
        long slept;
        public void sleep(int ms) { slept += ms; }
        public FamilyDns.Lookup lookupFreshName(int ms) {
            lookups++;
            if (ms != FamilyDns.FRESH_MS) return new FamilyDns.Lookup(FamilyDns.Lookup.Kind.FAILED, 0);
            if (lookups <= failFirst) return new FamilyDns.Lookup(FamilyDns.Lookup.Kind.FAILED, 100);
            return new FamilyDns.Lookup(lookup, lookupMs);
        }
        public boolean internetWorks(int ms) { return internet && ms == FamilyDns.HTTP_MS; }
        public long checkingSince() { return checking; }
        public boolean markChecking() { checking = 111; return true; }
        public void clearChecking() { checking = 0; }
        public FamilyDns.Record loadRecord() { return rec; }
        public boolean saveRecord(FamilyDns.Record r) { if (saveOk) rec = r; return saveOk; }
        public void clearRecord() { rec = null; }
        String state() { return g.get("private_dns_mode") + "/" + g.get("private_dns_specifier"); }
    }

    static Fake tv(String mode, String spec) {
        Fake f = new Fake();
        if (mode != null) f.g.put("private_dns_mode", mode);
        if (spec != null) f.g.put("private_dns_specifier", spec);
        return f;
    }

    public static void main(String[] a) {
        // 0 constants in one place
        System.out.println(FamilyDns.HOST + " " + FamilyDns.PORT);
        // 1 no grant
        Fake f = tv("opportunistic", null); f.grant = false;
        System.out.println(new FamilyDns(f).enable() + " " + f.writes + " " + f.state() + " " + (f.rec == null));
        // 2 no route: nothing changed, nothing recorded
        f = tv("opportunistic", null); f.route = false;
        System.out.println(new FamilyDns(f).enable() + " " + f.writes + " " + f.state() + " " + (f.rec == null));
        // 3 enable then exact restore (both originals set)
        f = tv("hostname", "dns.example");
        FamilyDns.Result r1 = new FamilyDns(f).enable();
        String on = f.state();
        FamilyDns.Result r2 = new FamilyDns(f).disable();
        System.out.println(r1 + " " + on + " " + r2 + " " + f.state() + " " + (f.rec == null));
        // 4 enable then restore where both were not set: they are deleted
        f = tv(null, null);
        new FamilyDns(f).enable();
        String on2 = f.state();
        new FamilyDns(f).disable();
        System.out.println(on2 + " " + f.g.containsKey("private_dns_mode") + " " + f.g.containsKey("private_dns_specifier") + " " + (f.rec == null));
        // 5 internet breaks: originals back at once (mode set, specifier null -> deleted)
        f = tv("off", null); f.internet = false;
        FamilyDns.Result r5 = new FamilyDns(f).enable();
        System.out.println(r5 + " " + f.state() + " " + f.g.containsKey("private_dns_specifier") + " " + (f.rec == null));
        // 6 second enable keeps the first record
        f = tv("opportunistic", "x.example");
        new FamilyDns(f).enable();
        FamilyDns.Result r6 = new FamilyDns(f).enable();
        System.out.println(r6 + " " + f.rec.mode + " " + f.rec.specifier);
        new FamilyDns(f).disable();
        System.out.println(f.state());
        // 7 unreadable value: not recorded as null, nothing changed
        f = tv("opportunistic", null); f.unreadable.add("private_dns_specifier");
        FamilyDns.Result r7 = new FamilyDns(f).enable();
        System.out.println(r7 + " " + f.writes + " " + (f.rec == null));
        // 8 value not taken (the TV ignores the write): originals put back, record cleared
        f = tv("opportunistic", "keep.example"); f.ignorePuts = true;
        FamilyDns.Result r8 = new FamilyDns(f).enable();
        System.out.println(r8 + " " + f.state() + " " + (f.rec == null));
        // 9 disable without a record changes nothing
        f = tv("hostname", "other.example");
        FamilyDns.Result r9 = new FamilyDns(f).disable();
        System.out.println(r9 + " " + f.writes + " " + f.state());
        // 10 status
        f = tv("opportunistic", null);
        FamilyDns d = new FamilyDns(f);
        String s1 = d.status().toString();
        d.enable();
        String s2 = d.status().toString();
        d.disable();
        f.unreadable.add("private_dns_mode");
        String s3 = d.status().toString();
        Fake o = tv("hostname", FamilyDns.HOST);
        System.out.println(s1 + " | " + s2 + " | " + s3 + " | " + new FamilyDns(o).status() + " | " + new FamilyDns(o).enable() + " " + o.writes + " " + (o.rec == null));
        // 11 record cannot be saved: nothing changed
        f = tv("opportunistic", null); f.saveOk = false;
        System.out.println(new FamilyDns(f).enable() + " " + f.writes);
        // 12 Arabic copy
        System.out.println(FamilyDns.arabic(FamilyDns.Result.NO_ROUTE) + " | " + FamilyDns.arabic(FamilyDns.Result.REVERTED) + " | " + FamilyDns.arabic(FamilyDns.Result.NEEDS_GRANT));
    
        // 13 Private DNS below API 28: UNSUPPORTED, nothing written, never ON
        f = tv("hostname", FamilyDns.HOST); f.sdk = 27;
        Fake f27 = tv("opportunistic", null); f27.sdk = 27;
        System.out.println(new FamilyDns(f27).enable() + " " + f27.writes + " " + (f27.rec == null) + " " + new FamilyDns(f27).supported()
            + " " + new FamilyDns(f).status() + " " + new FamilyDns(tv(null, null)).supported());
        // 14 Private DNS never validates (cached DNS would still answer): REVERTED, originals back, flag and record cleared
        f = tv("opportunistic", "x.example"); f.validated = false;
        FamilyDns.Result r14 = new FamilyDns(f).enable();
        System.out.println(r14 + " " + f.state() + " " + (f.rec == null) + " " + f.checking);
        // 15 the flag is saved before the first write and cleared after ENABLED
        f = tv("opportunistic", null);
        FamilyDns.Result r15 = new FamilyDns(f).enable();
        System.out.println(r15 + " " + f.checkingAtFirstPut + " " + f.checking + " " + (f.rec != null));
        // 16 recover: a cut-short check (flag set, record, our values on) goes back; no flag = nothing; flag without record = flag cleared
        f = tv("hostname", FamilyDns.HOST); f.rec = new FamilyDns.Record("opportunistic", null); f.checking = 5;
        FamilyDns.Result r16 = new FamilyDns(f).recover();
        Fake nf = tv("hostname", FamilyDns.HOST); nf.rec = new FamilyDns.Record("off", null);
        FamilyDns.Result r16b = new FamilyDns(nf).recover();
        Fake of = tv("hostname", "o.example"); of.checking = 5;
        FamilyDns.Result r16c = new FamilyDns(of).recover();
        System.out.println(r16 + " " + f.state() + " " + f.g.containsKey("private_dns_specifier") + " " + f.checking + " " + (f.rec == null)
            + " | " + r16b + " " + nf.writes + " | " + r16c + " " + of.checking + " " + of.writes);
        // 17 off when the value is no longer ours: nothing written, record dropped
        f = tv("opportunistic", "x.example");
        new FamilyDns(f).enable();
        f.g.put("private_dns_specifier", "mine.example");
        int w = f.writes;
        FamilyDns.Result r17 = new FamilyDns(f).disable();
        System.out.println(r17 + " " + (f.writes - w) + " " + f.state() + " " + (f.rec == null));
        // 18 Arabic copy for the new results
        System.out.println(FamilyDns.arabic(FamilyDns.Result.UNSUPPORTED) + " | " + FamilyDns.arabic(FamilyDns.Result.CHANGED_BY_SOMEONE_ELSE));
        // 19 fresh name: fast NXDOMAIN and a resolved address continue; timeout, slow NXDOMAIN, other failure revert with the originals back
        StringBuilder sb = new StringBuilder();
        Object[][] cases = {{FamilyDns.Lookup.Kind.NOT_FOUND, 50L}, {FamilyDns.Lookup.Kind.RESOLVED, 3500L},
            {FamilyDns.Lookup.Kind.TIMEOUT, 6000L}, {FamilyDns.Lookup.Kind.NOT_FOUND, 6500L}, {FamilyDns.Lookup.Kind.FAILED, 10L}};
        for (Object[] c : cases) {
            f = tv("opportunistic", "x.example"); f.lookup = (FamilyDns.Lookup.Kind) c[0]; f.lookupMs = (Long) c[1];
            FamilyDns.Result r = new FamilyDns(f).enable();
            sb.append(r).append(' ').append(f.state()).append(' ').append(f.rec == null).append(f.checking).append(" | ");
        }
        // the lookup is not made when Private DNS never shows active, and not in the revert-by-internet case before it
        f = tv("opportunistic", "x.example"); f.validated = false;
        new FamilyDns(f).enable();
        sb.append(f.lookups);
        System.out.println(sb);
        // 20 an UnknownHostException is «no such name» only with a GaiException EAI_NODATA/EAI_NONAME cause
        System.out.println(FamilyDns.unknownHostKind(true, 7, 7, 8) + " " + FamilyDns.unknownHostKind(true, 8, 7, 8) + " "
            + FamilyDns.unknownHostKind(true, 2, 7, 8) + " " + FamilyDns.unknownHostKind(false, 7, 7, 8));
        // 21 validated Private DNS servers: none = not working; a TV that cannot say (null) is not refused
        System.out.println(FamilyDns.validatedServersOk(0) + " " + FamilyDns.validatedServersOk(1) + " " + FamilyDns.validatedServersOk(null));
        // 22 the check fails and the old values cannot be put back: RESTORE_FAILED (never REVERTED), our values still on, the record and the flag kept
        f = tv("opportunistic", "x.example"); f.validated = false; f.breakRestore = true;
        FamilyDns.Result r22 = new FamilyDns(f).enable();
        System.out.println(r22 + " " + f.state() + " " + (f.rec != null) + " " + (f.checking != 0));
        // 23 the same for the partial write (the specifier went in, the mode did not): RESTORE_FAILED, record and flag kept
        f = tv("opportunistic", "x.example"); f.failModePut = true; f.breakRestore = true;
        FamilyDns.Result r23 = new FamilyDns(f).enable();
        System.out.println(r23 + " " + (f.rec != null) + " " + (f.checking != 0));
        // 24 …and once the TV lets the values back, recover() puts them back (the record and the flag were kept for it)
        f.breakRestore = false;
        FamilyDns.Result r24 = new FamilyDns(f).recover();
        System.out.println(r24 + " " + f.state() + " " + (f.rec == null) + " " + f.checking);
        // 25 the old values did go back (read back equal): still REVERTED and NOT_TAKEN, record and flag cleared
        f = tv("opportunistic", "x.example"); f.validated = false;
        FamilyDns.Result r25 = new FamilyDns(f).enable();
        Fake nt = tv("opportunistic", "x.example"); nt.failModePut = true;
        FamilyDns.Result r25b = new FamilyDns(nt).enable();
        System.out.println(r25 + " " + (f.rec == null) + " " + f.checking + " | " + r25b + " " + nt.state() + " " + (nt.rec == null) + " " + nt.checking);
        // 26 retries: a resolver still validating (two fast failures) then answering is ENABLED; the same slot rule waits out the rest of each try
        f = tv("opportunistic", "x.example"); f.failFirst = 2; f.lookupMs = 4500;   // a slow first answer (TLS handshake) is accepted now
        FamilyDns.Result r26 = new FamilyDns(f).enable();
        Fake nv = tv("opportunistic", "x.example"); nv.failFirst = 99;   // never answers: three tries, then REVERTED, originals back
        FamilyDns.Result r26b = new FamilyDns(nv).enable();
        Fake one = tv("opportunistic", "x.example");   // answers at once: one lookup, no sleep
        FamilyDns.Result r26c = new FamilyDns(one).enable();
        System.out.println(r26 + " " + f.lookups + " " + f.slept + " | " + r26b + " " + nv.lookups + " " + nv.state() + " " + (nv.rec == null) + " " + nv.checking
            + " | " + r26c + " " + one.lookups + " " + one.slept);
        // 27 the budget, and the resolver's own text as «no such name» (a TV that hides GaiException.error)
        System.out.println(FamilyDns.INTERNET_MS + " " + (FamilyDns.VALIDATE_MS + FamilyDns.FRESH_TRIES * FamilyDns.FRESH_MS + FamilyDns.HTTP_MS) + " " + FamilyDns.HTTP_MS
            + " | " + FamilyDns.messageIsNoSuchName("android_getaddrinfo failed: EAI_NODATA (No address associated with hostname)")
            + " " + FamilyDns.messageIsNoSuchName("Unable to resolve host \"a.b\": No address associated with hostname")
            + " " + FamilyDns.messageIsNoSuchName("android_getaddrinfo failed: EAI_AGAIN (Temporary failure in name resolution)") + " " + FamilyDns.messageIsNoSuchName(null));
    }
}
