package sa.urtv.home;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Shahid's live channels as posters on the home screen: what is on now, its time and how far along it is.
 * Shahid publishes no live channels to the TV's program database, but its public API (no account) has the
 * channels' pictures and the program guide, and its app opens a channel from a livestream link.
 */
final class ShahidLive {
    static final String PKG = "net.mbc.shahidTV";

    /** id, the name shown, in the order of the row; the government channels have no guide in Shahid */
    static final String[][] CHANNELS = {
        {"387238", "MBC 1"}, {"400917", "MBC 2"}, {"387251", "MBC دراما"}, {"400921", "MBC أكشن"},
        {"387290", "MBC مصر"}, {"387286", "العربية"}, {"946948", "الإخبارية"}, {"946938", "السعودية"},
        {"946940", "SBC"}, {"946945", "ذكريات"}, {"999927", "السعودية الآن"}, {"49923857275647", "اقرأ"},
        {"946946", "القرآن الكريم"}, {"946942", "السنة النبوية"}, {"387296", "MBC دراما بلس"},
        {"387293", "MBC مصر 2"}, {"49923122575716", "مصر دراما"}, {"387937", "MBC 5"}, {"400919", "MBC 4"},
        {"409385", "MBC 3"}, {"409387", "MBC بوليود"}, {"400924", "MBC ماكس"},
    };

    private static final String API = "https://api3.shahid.net/proxy/v2.1/";
    private static final long GUIDE_MS = 10 * 60 * 1000, PICTURES_MS = 24 * 3600 * 1000;

    private static final class Show { String title, poster; long from, to; }

    private static final Map<String, String> pictures = new HashMap<>();   // channel id → its picture
    private static final Map<String, List<Show>> guide = new HashMap<>();
    private static long guideAt, picturesAt;

    /** Fetches what is stale (in the background): the channels' pictures once a day, the guide every 10 minutes. */
    static synchronized void refresh() {
        long now = System.currentTimeMillis();
        if (now - picturesAt > PICTURES_MS) try {
            for (int page = 0; page < 3; page++) {
                String filter = "{\"pageNumber\":" + page + ",\"pageSize\":30,\"productType\":\"LIVESTREAM\",\"productSubType\":\"LIVE_CHANNEL\"}";
                JSONObject j = new JSONObject(get(API + "product/filter?filter=" + URLEncoder.encode(filter, "UTF-8") + "&country=SA&language=ar"));
                JSONArray ps = j.getJSONObject("productList").getJSONArray("products");
                for (int i = 0; i < ps.length(); i++) {
                    JSONObject p = ps.getJSONObject(i);
                    JSONObject img = p.optJSONObject("image");
                    String pic = img == null ? "" : img.optString("thumbnailImage", "");
                    if (!pic.isEmpty()) pictures.put(String.valueOf(p.getLong("id")), pic);
                }
                if (ps.length() < 30) break;
            }
            picturesAt = now;
        } catch (Exception ignored) { }
        if (now - guideAt > GUIDE_MS) try {
            StringBuilder ids = new StringBuilder();
            for (String[] c : CHANNELS) ids.append(ids.length() == 0 ? "" : ",").append(c[0]);
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:00:00.000'Z'", Locale.ROOT);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            String from = iso.format(new Date(now - 3 * 3600 * 1000L)), to = iso.format(new Date(now + 8 * 3600 * 1000L));
            JSONObject j = new JSONObject(get(API + "shahid-epg-api/?csvChannelIds=" + ids + "&from=" + from + "&to=" + to
                    + "&country=SA&language=ar&Accept-Language=ar"));
            JSONArray chans = j.getJSONArray("items");
            guide.clear();
            for (int i = 0; i < chans.length(); i++) {
                JSONObject ch = chans.getJSONObject(i);
                JSONArray items = ch.getJSONArray("items");
                List<Show> shows = new ArrayList<>();
                for (int k = 0; k < items.length(); k++) {
                    JSONObject it = items.getJSONObject(k);
                    if (it.optBoolean("emptySlot")) continue;
                    String title = it.optString("title", "").replaceAll("\\s+", " ").trim();
                    if (title.isEmpty() || title.contains("غير متاح")) continue;   // «جدول البرامج غير متاح»
                    Show s = new Show();
                    s.title = title;
                    s.poster = it.optString("productPoster", "");
                    s.from = time(it.optString("from"));
                    s.to = time(it.optString("to"));
                    if (s.from > 0 && s.to > s.from) shows.add(s);
                }
                guide.put(ch.getString("channelId"), shows);
            }
            guideAt = now;
        } catch (Exception ignored) { }
    }

    /** The chosen channels as posters, worked out now: the show on, its times, how much has played. */
    static synchronized List<Showcase.Item> items(java.util.Set<String> hidden) {
        List<Showcase.Item> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String[] c : CHANNELS) {
            if (hidden.contains(c[0])) continue;
            Show on = null, next = null;
            List<Show> shows = guide.get(c[0]);
            if (shows != null) for (Show s : shows) {
                if (s.from <= now && now < s.to) on = s;
                else if (s.from >= now && (next == null || s.from < next.from)) next = s;
            }
            Showcase.Item it = new Showcase.Item();
            it.pkg = PKG;
            it.group = "البث المباشر · " + c[1];
            it.live = true;
            String channelPic = picture(pictures.get(c[0]), 640, 360);
            if (on != null) {
                it.title = on.title;
                long left = (on.to - now) / 60000;
                it.detail = on.title + " · " + clock(on.from) + " – " + clock(on.to)
                        + (left > 0 ? " · بقي " + minutes(left) : "")
                        + (next != null ? "\nبعده: " + next.title + " " + clock(next.from) : "");
                it.progress = (int) Math.min(100, (now - on.from) * 100 / Math.max(1, on.to - on.from));
                String p = picture(on.poster, 640, 360);
                it.poster = p != null ? p : channelPic;
            } else {
                it.title = c[1];
                it.detail = c[1] + " · بث مباشر";
                it.poster = channelPic;
            }
            it.wide = channelPic != null ? channelPic : it.poster;
            it.aspect = 16 / 9f;
            it.intent = "intent://shahid.mbc.net/ar/livestream/channel/livechannel-" + c[0]
                    + "#Intent;scheme=https;package=" + PKG + ";end";
            out.add(it);
        }
        return out;
    }

    /** «دقيقة واحدة»، «دقيقتان»، «٧ دقائق»، «٢٥ دقيقة» */
    static String minutes(long n) {
        if (n == 1) return "دقيقة واحدة";
        if (n == 2) return "دقيقتان";
        return MainActivity.arabicDigits(String.valueOf(n)) + (n >= 3 && n <= 10 ? " دقائق" : " دقيقة");
    }

    /** Shahid's picture links are templates: {width}, {height}, {croppingPoint} */
    private static String picture(String t, int w, int h) {
        if (t == null || t.isEmpty()) return null;
        return t.replace("{width}", String.valueOf(w)).replace("{height}", String.valueOf(h)).replace("{croppingPoint}", "mc");
    }

    private static String clock(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        int h = c.get(Calendar.HOUR) == 0 ? 12 : c.get(Calendar.HOUR);
        return MainActivity.arabicDigits(h + ":" + String.format(Locale.ROOT, "%02d", c.get(Calendar.MINUTE)))
                + (c.get(Calendar.AM_PM) == Calendar.AM ? " ص" : " م");
    }

    private static long time(String s) {
        try {
            // 2026-10-21T00:15:00.022+00:00
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(s.substring(0, 19)).getTime();
        } catch (Exception e) { return 0; }
    }

    private static String get(String url) throws java.io.IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            for (int n; (n = in.read(b)) > 0; ) o.write(b, 0, n);
            return o.toString("UTF-8");
        }
    }
}
