package sa.urtv.notify;

import org.json.JSONObject;

import java.util.Map;

/** One pop-up: text, an optional icon and picture, or a camera/video stream. */
final class Note {
    // Position codes are Home Assistant's (notifications_android_tv): 0 bottom right, 1 bottom left,
    // 2 top right, 3 top left, 4 centre.
    static final int BOTTOM_RIGHT = 0, BOTTOM_LEFT = 1, TOP_RIGHT = 2, TOP_LEFT = 3, CENTER = 4;

    String title = "", message = "";
    int seconds = 5;
    int position = BOTTOM_RIGHT;
    float textScale = 1f;
    float alpha = 1f;
    int color = 0xFF263238;
    byte[] icon, image;
    String imageUrl, videoUrl;
    int mediaWidthPx = 640;   // PiPup's «width» is in screen pixels
    boolean sound = true;     // the chime; a form field or JSON «sound»: false turns it off

    /** Home Assistant's «Notifications for Android TV / Fire TV»: multipart form fields and files. */
    static Note fromForm(Map<String, String> fields, Map<String, byte[]> files) {
        // Home Assistant's integration sends every field as a file part; only the two pictures are files
        Map<String, String> f = new java.util.HashMap<>(fields);
        for (Map.Entry<String, byte[]> e : files.entrySet())
            if (!e.getKey().startsWith("filename")) f.put(e.getKey(), new String(e.getValue(), java.nio.charset.StandardCharsets.UTF_8));
        Note n = new Note();
        n.title = f.getOrDefault("title", "");
        if ("Notification".equals(n.title)) n.title = "";   // the library's default title
        n.message = f.getOrDefault("msg", "");
        n.seconds = clamp(parseInt(f.get("duration"), 5), 1, 600);
        n.position = clamp(parseInt(f.get("position"), BOTTOM_RIGHT), 0, 4);
        switch (parseInt(f.get("fontsize"), 0)) {
            case 1: n.textScale = 0.85f; break;
            case 2: n.textScale = 1.25f; break;
            case 3: n.textScale = 1.6f; break;
            default: n.textScale = 1f;
        }
        // 1: 0% transparent … 5: 100%
        n.alpha = 1f - (clamp(parseInt(f.get("transparency"), 1), 1, 5) - 1) * 0.25f;
        n.color = parseColor(f.containsKey("bkgcolor") ? f.get("bkgcolor") : f.get("color"), n.color);
        n.icon = usable(files.get("filename"));
        n.image = usable(files.get("filename2"));
        n.sound = !"false".equalsIgnoreCase(f.getOrDefault("sound", "true").trim());
        return n;
    }

    /** PiPup's JSON: {"title","message","duration","position","backgroundColor","media":{"image"|"video":{"uri","width"}}}. */
    static Note fromPiPup(JSONObject j) {
        Note n = new Note();
        n.title = j.optString("title", "");
        n.message = j.optString("message", "");
        n.seconds = clamp(j.optInt("duration", 30), 1, 600);
        // PiPup: 0 top right, 1 top left, 2 bottom right, 3 bottom left, 4 centre
        int[] map = {TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT, CENTER};
        n.position = map[clamp(j.optInt("position", 0), 0, 4)];
        n.color = parseColor(j.optString("backgroundColor", null), n.color);
        n.sound = j.optBoolean("sound", true);
        JSONObject media = j.optJSONObject("media");
        if (media != null) {
            JSONObject v = media.optJSONObject("video"), i = media.optJSONObject("image");
            if (v != null) { n.videoUrl = v.optString("uri", null); n.mediaWidthPx = v.optInt("width", 640); }
            else if (i != null) { n.imageUrl = i.optString("uri", null); n.mediaWidthPx = i.optInt("width", 640); }
        }
        return n;
    }

    /** True when the text is written in Arabic: the card is then laid out right to left. */
    boolean isArabic() {
        String s = title + message;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if ((c >= 0x0600 && c <= 0x06FF) || (c >= 0x0750 && c <= 0x077F) || (c >= 0xFB50 && c <= 0xFEFF)) return true;
            if (Character.isLetter(c)) return false;
        }
        return false;
    }

    /** The library always attaches a 1×1 placeholder icon: ignore it. */
    private static byte[] usable(byte[] b) { return b != null && b.length > 100 ? b : null; }

    private static int parseInt(String s, int def) {
        try { return s == null ? def : Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static int parseColor(String s, int def) {
        try { return s == null || s.isEmpty() ? def : android.graphics.Color.parseColor(s.trim()); }
        catch (IllegalArgumentException e) { return def; }
    }
}
