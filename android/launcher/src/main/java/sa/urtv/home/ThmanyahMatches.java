package sa.urtv.home;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * ثمانية publishes nothing for the home screen; it carries the Saudi league, so its posters are this week's
 * league matches (TheSportsDB, the key the screensaver uses): last round's results and the coming games.
 */
final class ThmanyahMatches {
    static final String PKG = "com.thmanyah.shasha.tv";
    static final String LEAGUE = "دوري روشن السعودي";
    private static final String API = "https://www.thesportsdb.com/api/v1/json/123/";
    private static final String LEAGUE_ID = "4668";

    static final class Match {
        String home, away, homeBadge, awayBadge, venue, day, time;
        int homeScore = -1, awayScore = -1;
        boolean live, postponed;
        long at;
    }

    private static final List<Match> matches = new ArrayList<>();
    private static long fetchedAt;
    private static Map<String, String> arabic;

    /** Every 10 minutes, every 2 while a match is on. */
    static synchronized void refresh(Context c) {
        boolean anyLive = false;
        for (Match mt : matches) anyLive |= mt.live;
        long now = System.currentTimeMillis();
        if (now - fetchedAt < (anyLive ? 2 : 10) * 60 * 1000) return;
        try {
            if (arabic == null) arabic = names(c);
            Set<String> rounds = new LinkedHashSet<>();
            String season = null;
            for (String q : new String[]{"eventspastleague.php", "eventsnextleague.php"}) {
                JSONArray e = new JSONObject(get(API + q + "?id=" + LEAGUE_ID)).optJSONArray("events");
                if (e == null || e.length() == 0) continue;
                JSONObject ev = e.getJSONObject(0);
                rounds.add(ev.optString("intRound"));
                season = ev.optString("strSeason");
            }
            if (season == null) return;
            List<Match> out = new ArrayList<>();
            for (String r : rounds) {
                JSONArray e = new JSONObject(get(API + "eventsround.php?id=" + LEAGUE_ID + "&r=" + r + "&s=" + season)).optJSONArray("events");
                if (e == null) continue;
                for (int i = 0; i < e.length(); i++) {
                    Match mt = match(e.getJSONObject(i));
                    // this week: a week back, a week ahead
                    if (mt != null && Math.abs(mt.at - now) < 7L * 24 * 3600 * 1000) out.add(mt);
                }
            }
            Collections.sort(out, (a, b) -> Long.compare(a.at, b.at));
            matches.clear();
            matches.addAll(out);
            fetchedAt = now;
            android.util.Log.i("UrTV", "league: " + out.size() + " matches this week, rounds " + rounds);
        } catch (Exception e) { android.util.Log.w("UrTV", "league: " + e); }
    }

    private static Match match(JSONObject e) {
        Match mt = new Match();
        mt.home = name(e.optString("idHomeTeam"), e.optString("strHomeTeam"));
        mt.away = name(e.optString("idAwayTeam"), e.optString("strAwayTeam"));
        mt.homeBadge = badge(e.optString("strHomeTeamBadge"));
        mt.awayBadge = badge(e.optString("strAwayTeamBadge"));
        mt.venue = VENUES.get(e.optString("strVenue").trim());
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            mt.at = f.parse(e.optString("dateEvent") + " " + (e.optString("strTime", "00:00:00").isEmpty() ? "00:00:00" : e.optString("strTime"))).getTime();
        } catch (Exception ex) { return null; }
        String st = e.optString("strStatus", "");
        mt.postponed = st.equals("PST") || st.equals("CANC") || st.equals("ABD");
        boolean done = st.equals("FT") || st.equals("AET") || st.equals("PEN") || st.equals("AP");
        mt.live = !mt.postponed && !done && !st.isEmpty() && !st.equals("NS") && !st.equals("TBD");
        if ((done || mt.live) && !e.isNull("intHomeScore") && !e.isNull("intAwayScore")) {
            mt.homeScore = e.optInt("intHomeScore");
            mt.awayScore = e.optInt("intAwayScore");
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(mt.at);
        mt.day = DAYS[c.get(Calendar.DAY_OF_WEEK) - 1];
        int h = c.get(Calendar.HOUR) == 0 ? 12 : c.get(Calendar.HOUR);
        mt.time = MainActivity.arabicDigits(h + ":" + String.format(Locale.ROOT, "%02d", c.get(Calendar.MINUTE)))
                + (c.get(Calendar.AM_PM) == Calendar.AM ? " ص" : " م");
        return mt;
    }

    /**
     * The posters, worked out now. The row starts on today's first match; with none today, on the next day that
     * has some; yesterday's and the rest of the week's are one press to the right.
     */
    static synchronized List<Showcase.Item> items() {
        List<Showcase.Item> out = new ArrayList<>();
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        int start = -1;
        for (Match mt : matches) {
            Showcase.Item it = new Showcase.Item();
            it.pkg = PKG;
            it.match = mt;
            it.title = mt.home + " × " + mt.away;
            it.group = LEAGUE;
            it.detail = "";
            it.aspect = 16 / 9f;
            if (start < 0 && mt.at >= midnight.getTimeInMillis()) start = out.size();   // sorted by time
            out.add(it);
        }
        if (start < 0) start = out.size() - 1;   // the week is over: its last match
        if (start >= 0) out.get(start).startHere = true;
        return out;
    }

    private static String name(String id, String en) {
        String ar = arabic == null ? null : arabic.get(id);
        return ar != null && !ar.isEmpty() ? ar : en;
    }

    private static String badge(String url) { return url == null || url.isEmpty() ? null : url + "/small"; }

    private static Map<String, String> names(Context c) {
        Map<String, String> out = new HashMap<>();
        try (InputStream in = c.getAssets().open("data/teams.json")) {
            JSONArray a = new JSONArray(read(in));
            for (int i = 0; i < a.length(); i++) out.put(a.getJSONObject(i).optString("id"), a.getJSONObject(i).optString("ar"));
        } catch (Exception ignored) { }
        return out;
    }

    private static final String[] DAYS = {"الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};

    /** the league's grounds in Arabic; one not listed is left off the card rather than shown in English */
    private static final Map<String, String> VENUES = new HashMap<>();
    static {
        String[][] v = {
            {"Kingdom Arena", "المملكة أرينا"},
            {"King Abdullah Sports City", "مدينة الملك عبدالله الرياضية"},
            {"King Abdullah Sports City Stadium", "مدينة الملك عبدالله الرياضية"},
            {"King Saud University Stadium", "ملعب جامعة الملك سعود"},
            {"King Fahd International Stadium", "استاد الملك فهد الدولي"},
            {"King Khalid Sport City Stadium", "مدينة الملك خالد الرياضية"},
            {"Prince Sultan bin Abdulaziz Sports City Stadium", "مدينة الأمير سلطان بن عبدالعزيز الرياضية"},
            {"Prince Turki bin Abdul Aziz Stadium", "ملعب الأمير تركي بن عبدالعزيز"},
            {"Prince Mohamed bin Fahd Stadium", "ملعب الأمير محمد بن فهد"},
            {"Prince Abdullah bin Jalawi Stadium", "ملعب الأمير عبدالله بن جلوي"},
            {"Al-Ettifaq Club Stadium", "ملعب نادي الاتفاق"},
            {"Al-Shabab Club Stadium", "ملعب نادي الشباب"},
            {"Al-Hazem Club Stadium", "ملعب نادي الحزم"},
            {"Al-Fateh Stadium", "ملعب نادي الفتح"},
            {"Al-Majma'ah Sports City Stadium", "مدينة المجمعة الرياضية"},
        };
        for (String[] x : v) VENUES.put(x[0], x[1]);
    }

    private static String get(String url) throws java.io.IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        try (InputStream in = c.getInputStream()) { return read(in); }
    }

    private static String read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        for (int n; (n = in.read(b)) > 0; ) o.write(b, 0, n);
        return o.toString("UTF-8");
    }
}
