package sa.urtv.home;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * «إعدادات تلفزيونك»: the TV's settings as a TV screen, in Arabic, right to left. Three columns: the sections at
 * the right, the chosen section's controls in the middle, and a live preview or a note at the left.
 *
 * Controls work in place where Android lets an app: switches (OK), choices (Left/Right), the volume (Left/Right),
 * lists with a search box already at the top. Only what Android keeps to itself opens a system screen (joining a
 * Wi-Fi network, pairing, picture modes). The screensaver's settings go through SaverAppReceiver (same key).
 */
public class TvSettings extends Activity {
    private static final int GOLD = 0xFFD6AF6B, TEXT = 0xFFF6EEDC, SUB = 0xFF9C9279, ROW = 0x14FFFFFF;
    private static final Interpolator EASE = new PathInterpolator(0.32f, 0.72f, 0f, 1f);
    private static final String SAVER = MainActivity.SAVER_PKG;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Typeface regular, medium, display;
    private LinearLayout sectionList;
    private FrameLayout middle;
    private TextView noteTitle, noteText;
    private FrameLayout previewBox;
    private WebView preview;

    /** pages shown in the middle column; Back closes the top one */
    private final List<View> stack = new ArrayList<>();
    private final List<View> sectionViews = new ArrayList<>();
    private int section = 0;
    private Map<String, String> saver = new HashMap<>();

    // ================================================================ set-up

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            regular = Typeface.createFromAsset(getAssets(), "fonts/Thmanyah-Regular.otf");
            medium = Typeface.createFromAsset(getAssets(), "fonts/Thmanyah-Medium.otf");
            display = Typeface.createFromAsset(getAssets(), "fonts/ThmanyahDisplay-Bold.otf");
        } catch (RuntimeException e) { regular = Typeface.DEFAULT; medium = display = Typeface.DEFAULT_BOLD; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TR_BL, new int[]{0xFF0C1813, 0xFF060B09}));
        root.setPadding(dp(32), dp(28), dp(32), dp(24));
        // children left to right: preview | controls | sections (the sections are at the right, where Arabic starts)
        root.addView(buildNote(), new LinearLayout.LayoutParams(dp(220), -1));
        middle = new FrameLayout(this);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -1, 1f);
        mp.leftMargin = dp(24);
        mp.rightMargin = dp(24);
        root.addView(middle, mp);
        root.addView(buildSections(), new LinearLayout.LayoutParams(dp(190), -1));
        setContentView(root);

        root.setAlpha(0f);
        root.setTranslationX(dp(40));
        root.animate().alpha(1f).translationX(0).setDuration(380).setInterpolator(EASE).start();

        loadSaver(() -> { if (section == SECTION_SAVER) refreshTop(); });
        showSection(0, false);
        sectionViews.get(0).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // back from a system screen: values may have changed
        if (!stack.isEmpty()) refreshTop();
    }

    @Override
    public void onBackPressed() {
        View f = getCurrentFocus();
        if (stack.size() > 1) { popPage(); return; }
        if (f != null && isInMiddle(f)) { sectionViews.get(section).requestFocus(); return; }
        finish();
        overridePendingTransition(0, 0);
    }

    // ================================================================ columns

    private View buildSections() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text("الإعدادات", 28, display, TEXT), new LinearLayout.LayoutParams(-1, -2));
        TextView dev = text(cap(Build.MANUFACTURER) + " · أندرويد " + Build.VERSION.RELEASE, 12, regular, SUB);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
        dp.bottomMargin = dp(18);
        col.addView(dev, dp);
        sectionList = new LinearLayout(this);
        sectionList.setOrientation(LinearLayout.VERTICAL);
        col.addView(sectionList, new LinearLayout.LayoutParams(-1, -2));
        String[][] names = {
            {"الشبكة", "wifi"}, {"البلوتوث", "bt"}, {"الصوت", "sound"}, {"الصورة", "display"},
            {"شاشة التوقف", "saver"}, {"المفضلة", "dock"}, {"التنبيهات", "notify"}, {"النظام", "system"},
        };
        int[] icons = {R.drawable.ic_wifi, R.drawable.ic_bluetooth, R.drawable.ic_volume, R.drawable.ic_display,
                R.drawable.ic_screensaver, R.drawable.ic_star, R.drawable.ic_bell, R.drawable.ic_info};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            r.setPadding(dp(14), 0, dp(14), 0);
            r.setFocusable(true);
            r.setId(View.generateViewId());
            GradientDrawable bg = rounded(0x00000000, 10);
            r.setBackground(bg);
            TextView t = text(names[i][0], 15, medium, 0xCCF6EEDC);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            ImageView ic = icon(icons[i], 0xCCD6AF6B);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(20), dp(20));
            ip.leftMargin = dp(12);
            r.addView(ic, ip);
            r.setOnFocusChangeListener((v, has) -> {
                bg.setColor(has ? 0x26DCB86F : (idx == section ? 0x12FFFFFF : 0));
                bg.setStroke(dp(1), has ? 0xCCDCB86F : 0);
                t.setTextColor(has ? TEXT : 0xCCF6EEDC);
                if (has && idx != section && !swapping) showSection(idx, true);
            });
            r.setOnClickListener(v -> focusMiddle());
            r.setOnKeyListener((v, code, e) -> {
                if (e.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_LEFT) { focusMiddle(); return true; }
                return false;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(38));
            lp.bottomMargin = dp(3);
            sectionList.addView(r, lp);
            sectionViews.add(r);
        }
        for (int i = 0; i < sectionViews.size(); i++) {
            View v = sectionViews.get(i);
            v.setNextFocusUpId(sectionViews.get(Math.max(0, i - 1)).getId());
            v.setNextFocusDownId(sectionViews.get(Math.min(sectionViews.size() - 1, i + 1)).getId());
            v.setNextFocusRightId(v.getId());
        }
        return col;
    }

    private View buildNote() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(58), 0, 0);
        previewBox = new FrameLayout(this);
        previewBox.setClipChildren(true);
        previewBox.setBackground(rounded(0xFF000000, 10));
        previewBox.setClipToOutline(true);
        previewBox.setVisibility(View.GONE);
        col.addView(previewBox, new LinearLayout.LayoutParams(dp(220), dp(124)));
        noteTitle = text("", 15, medium, GOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(14);
        col.addView(noteTitle, tp);
        noteText = text("", 13, regular, SUB);
        noteText.setLineSpacing(0, 1.25f);
        col.addView(noteText, new LinearLayout.LayoutParams(-1, -2));
        return col;
    }

    private void note(String title, String body) {
        noteTitle.setText(title == null ? "" : title);
        noteText.setText(body == null ? "" : body);
    }

    // ================================================================ sections

    private static final int SECTION_WIFI = 0, SECTION_BT = 1, SECTION_SOUND = 2, SECTION_DISPLAY = 3,
            SECTION_SAVER = 4, SECTION_DOCK = 5, SECTION_NOTIFY = 6, SECTION_SYSTEM = 7;

    private void showSection(int idx, boolean animate) {
        int old = section;
        section = idx;
        if (old != idx && old < sectionViews.size()) sectionViews.get(old).getOnFocusChangeListener().onFocusChange(sectionViews.get(old), false);
        stack.clear();
        middle.removeAllViews();
        showPreview(idx == SECTION_SAVER);
        pushPage(buildRoot(idx), animate);
    }

    private Page buildRoot(int idx) {
        Page p = new Page(((TextView) ((LinearLayout) sectionViews.get(idx)).getChildAt(0)).getText().toString());
        switch (idx) {
            case SECTION_WIFI: wifiPage(p); break;
            case SECTION_BT: btPage(p); break;
            case SECTION_SOUND: soundPage(p); break;
            case SECTION_DISPLAY: displayPage(p); break;
            case SECTION_SAVER: saverPage(p); break;
            case SECTION_DOCK: dockPage(p); break;
            case SECTION_NOTIFY: notifyPage(p); break;
            default: systemPage(p);
        }
        return p;
    }

    /** the section's root page built again (values changed), swapped in with the focus on the same row */
    private void replaceRoot() {
        swapping = true;
        View oldRoot = stack.get(0);
        Page oldPage = (Page) oldRoot.getTag();
        View f = getCurrentFocus();
        boolean focused = stack.size() == 1 && f != null && isInMiddle(f);
        Page np = buildRoot(section);
        np.lastFocus = oldPage.lastFocus;
        middle.addView(np.view, 0, new FrameLayout.LayoutParams(-1, -1));
        if (stack.size() > 1) np.view.setVisibility(View.GONE);
        if (focused) np.focusAt(oldPage.lastFocus);
        stack.set(0, np.view);
        middle.removeView(oldRoot);
        swapping = false;
    }

    // ---------------------------------------------------------------- network

    @SuppressWarnings("deprecation")
    private void wifiPage(Page p) {
        note("الشبكة", "تشوف هنا اتصالك وقوته. تغيير الشبكة نفسه يفتح شاشة أندرويد، لأن أندرويد ما يسمح لأي تطبيق ثاني يغيّرها.");
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        LinkProperties lp = cm.getLinkProperties(cm.getActiveNetwork());
        String ip = "—";
        if (lp != null) for (android.net.LinkAddress a : lp.getLinkAddresses())
            if (a.getAddress() instanceof java.net.Inet4Address) ip = a.getAddress().getHostAddress();
        if (nc == null) {
            p.info("الحالة", "غير متصل");
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            p.info("الحالة", "متصل بالكيبل");
            p.info("العنوان", ip);
        } else {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            String ssid = wi == null || wi.getSSID() == null || wi.getSSID().contains("unknown") ? "متصل" : wi.getSSID().replace("\"", "");
            p.info("الشبكة", ssid);
            if (wi != null) {
                p.info("الإشارة", bars(WifiManager.calculateSignalLevel(wi.getRssi(), 5)) + "  " + wi.getRssi() + " dBm");
                p.info("النطاق", wi.getFrequency() > 4900 ? "5 جيجاهرتز" : "2.4 جيجاهرتز");
                p.info("السرعة", wi.getLinkSpeed() + " ميجابت/ث");
            }
            p.info("العنوان", ip);
            p.link("الشبكات القريبة", "", "كل الشبكات اللي يشوفها التلفزيون وقوتها", () -> pushPage(nearbyPage(), true));
        }
        p.link("تغيير الشبكة", "", "يفتح شاشة الواي فاي حق أندرويد", () -> system(new Intent(Settings.ACTION_WIFI_SETTINGS)));
    }

    @SuppressWarnings("deprecation")
    private Page nearbyPage() {
        Page p = new Page("الشبكات القريبة");
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        Map<String, ScanResult> best = new LinkedHashMap<>();
        try {
            for (ScanResult r : wm.getScanResults()) {
                if (r.SSID == null || r.SSID.isEmpty()) continue;
                ScanResult o = best.get(r.SSID);
                if (o == null || r.level > o.level) best.put(r.SSID, r);
            }
        } catch (SecurityException ignored) { }
        List<ScanResult> list = new ArrayList<>(best.values());
        Collections.sort(list, (a, b) -> b.level - a.level);
        if (list.isEmpty()) p.info("لا توجد نتائج", "جرّب بعد شوي");
        for (ScanResult r : list) {
            String band = r.frequency > 4900 ? "5G" : "2.4G";
            p.link(r.SSID, bars(WifiManager.calculateSignalLevel(r.level, 5)) + "  " + band, "الاتصال بها يفتح شاشة الواي فاي حق أندرويد",
                    () -> system(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        }
        return p;
    }

    // ---------------------------------------------------------------- bluetooth

    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    private void btPage(Page p) {
        note("البلوتوث", "تشغّله وتطفيه من هنا، وتشوف أجهزتك. إقران جهاز جديد يفتح شاشة أندرويد.");
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null) { p.info("البلوتوث", "غير متوفر في هذا التلفزيون"); return; }
        p.toggle("البلوتوث", "يشغّل البلوتوث أو يطفيه (الريموت الحالي ما يتأثر إذا كان بالأشعة)", a::isEnabled, on -> {
            try { if (on) a.enable(); else a.disable(); } catch (SecurityException ignored) { }
            main.postDelayed(this::refreshTop, 1500);
        });
        try {
            List<BluetoothDevice> bonded = new ArrayList<>(a.getBondedDevices());
            if (!bonded.isEmpty()) p.header("أجهزتك");
            for (BluetoothDevice d : bonded) p.info(d.getName() == null ? d.getAddress() : d.getName(), kind(d));
        } catch (SecurityException ignored) { }
        p.link("إقران جهاز جديد", "", "سماعة أو ريموت أو يد ألعاب", () -> system(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
    }

    @SuppressLint("MissingPermission")
    private static String kind(BluetoothDevice d) {
        BluetoothClass c = d.getBluetoothClass();
        if (c == null) return "جهاز";
        switch (c.getMajorDeviceClass()) {
            case BluetoothClass.Device.Major.AUDIO_VIDEO: return "سماعة أو صوت";
            case BluetoothClass.Device.Major.PERIPHERAL: return "ريموت أو لوحة مفاتيح";
            case BluetoothClass.Device.Major.PHONE: return "جوال";
            case BluetoothClass.Device.Major.COMPUTER: return "كمبيوتر";
            default: return "جهاز";
        }
    }

    // ---------------------------------------------------------------- sound

    private void soundPage(Page p) {
        note("الصوت", "يمين ويسار يغيّرون مستوى الصوت مباشرة. باقي إعدادات الصوت (المخرج، الصوت المحيطي) في شاشة أندرويد.");
        AudioManager am = getSystemService(AudioManager.class);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        p.slider("مستوى الصوت", max, () -> am.getStreamVolume(AudioManager.STREAM_MUSIC),
                v -> am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0));
        p.toggle("كتم الصوت", "", () -> am.isStreamMute(AudioManager.STREAM_MUSIC),
                on -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, on ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0));
        p.link("إعدادات الصوت المتقدمة", "", "المخرج، والصوت المحيطي، وأصوات النظام", () -> system(new Intent(Settings.ACTION_SOUND_SETTINGS)));
    }

    // ---------------------------------------------------------------- picture

    private void displayPage(Page p) {
        note("الصورة", "وضع الصورة والألوان من إعدادات سكاي وورث نفسها، وما يقدر تطبيق ثاني يغيّرها.");
        Display d = getWindowManager().getDefaultDisplay();
        Display.Mode m = d.getMode();
        int w = Math.max(m.getPhysicalWidth(), m.getPhysicalHeight()), h = Math.min(m.getPhysicalWidth(), m.getPhysicalHeight());
        p.info("الدقة", (h >= 2160 ? "4K" : h >= 1080 ? "Full HD" : "HD") + " · " + w + "×" + h);
        p.info("معدل التحديث", Math.round(m.getRefreshRate()) + " هرتز");
        Display.HdrCapabilities hdr = d.getHdrCapabilities();
        List<String> kinds = new ArrayList<>();
        if (hdr != null) for (int t : hdr.getSupportedHdrTypes())
            kinds.add(t == Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION ? "Dolby Vision" : t == Display.HdrCapabilities.HDR_TYPE_HDR10 ? "HDR10"
                    : t == Display.HdrCapabilities.HDR_TYPE_HLG ? "HLG" : "HDR10+");
        p.info("HDR", kinds.isEmpty() ? "غير مدعوم" : String.join("، ", kinds));
        p.link("وضع الصورة والألوان", "", "يفتح إعدادات الصورة حق التلفزيون", () -> system(new Intent(Settings.ACTION_DISPLAY_SETTINGS)));
    }

    // ---------------------------------------------------------------- screensaver

    private static final String[] PANEL_VALUES = {"verse", "prayers", "matches", "forecast", "shows", "none"};
    private static final String[] PANEL_NAMES = {"آية وحديث", "الصلاة", "المباريات", "الطقس", "المسلسلات", "لا شي"};

    private void saverPage(Page p) {
        note("شاشة التوقف", "المعاينة فوق تتحدّث مع كل تغيير.");
        if (saver.isEmpty()) { p.info("…", "جارٍ القراءة"); return; }
        p.choice("جنب الساعة", PANEL_NAMES, PANEL_VALUES, () -> saver.get("panel"), v -> setSaver("panel", v));
        p.choice("بطاقة الشاشة الرئيسية", PANEL_NAMES, PANEL_VALUES, () -> saver.get("home_panel"), v -> setSaver("home_panel", v));
        p.toggle("الأسواق", "تاسي، S&P 500، بيتكوين تحت الطقس", () -> !"off".equals(saver.get("markets")),
                on -> setSaver("markets", on ? "on" : "off"));
        p.link("المدينة", cityName(), "لمواقيت الصلاة والطقس", () -> pushPage(cityPage(), true));
        p.link("الفرق المتابعة", teamsLabel(), "ابحث عن أي فريق أو منتخب", () -> pushPage(teamsPage(), true));
        p.link("معاينة بملء الشاشة", "", "", () -> open(new Intent().setClassName(SAVER, SAVER + ".MainActivity")));
        p.link("باقي إعدادات شاشة التوقف", "", "المسلسلات، والتحديث، ووقت التشغيل",
                () -> open(new Intent().setClassName(SAVER, SAVER + ".SettingsActivity")));
    }

    private String cityName() {
        String c = saver.get("city");
        return c == null ? "" : c.replaceAll(" \\(.*", "");
    }

    private String teamsLabel() {
        List<String[]> t = teams();
        if (t.isEmpty()) return "لا شي";
        List<String> n = new ArrayList<>();
        for (String[] x : t) n.add(x[1]);
        return String.join("، ", n);
    }

    /** the followed teams as [id, name], in order */
    private List<String[]> teams() {
        List<String[]> out = new ArrayList<>();
        String s = saver.get("teams");
        if (s == null || s.trim().isEmpty()) return out;
        for (String part : s.split(", ")) {
            String[] kv = part.split("\\|", 2);
            if (kv.length == 2 && kv[0].matches("\\d+")) out.add(kv);
        }
        return out;
    }

    private void saveTeams(List<String[]> t) {
        List<String> parts = new ArrayList<>();
        for (String[] x : t) parts.add(x[0] + ":" + x[1].replace(",", " ").replace(":", " "));
        setSaver("teams", String.join(",", parts));
    }

    private Page teamsPage() {
        Page p = new Page("الفرق المتابعة");
        p.rebuild = this::teamsPage;
        note("الفرق المتابعة", "اكتب اسم الفريق أو المنتخب بالعربي أو الإنجليزي. الترتيب هنا هو ترتيبها في شاشة التوقف.");
        List<JSONObject> all = loadTeams();
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        EditText search = p.search("ابحث عن فريق أو منتخب", q -> {
            results.removeAllViews();
            if (q.trim().isEmpty()) return;
            int n = 0;
            for (JSONObject t : all) {
                if (!matches(t, q)) continue;
                results.addView(p.makeLink(t.optString("ar", t.optString("en")), league(t), "", () -> { p.lastFocus = 0; addTeam(t); }));
                if (++n >= 6) break;
            }
            if (n == 0) results.addView(p.makeInfo("ما لقيت", "جرّب الاسم بالإنجليزي"));
            p.relink();
        });
        p.add(results);
        List<String[]> mine = teams();
        if (!mine.isEmpty()) p.header("فرقك (" + mine.size() + " من 10)");
        for (int i = 0; i < mine.size(); i++) {
            final int idx = i;
            String[] t = mine.get(i);
            p.link(t[1], idx == 0 ? "الأول" : "", "اضغط للنقل أو الإزالة", () -> pushPage(teamMenu(idx), true));
        }
        p.header("تصفّح الدوريات");
        Map<String, List<JSONObject>> byLeague = new LinkedHashMap<>();
        for (JSONObject t : all) {
            JSONArray ls = t.optJSONArray("leagues");
            String l = ls != null && ls.length() > 0 ? ls.optString(0) : "أخرى";
            if (!byLeague.containsKey(l)) byLeague.put(l, new ArrayList<>());
            byLeague.get(l).add(t);
        }
        for (Map.Entry<String, List<JSONObject>> e : byLeague.entrySet())
            p.link(leagueName(e.getKey()), e.getValue().size() + " فريق", "", () -> pushPage(leaguePage(e.getKey(), e.getValue()), true));
        return p;
    }

    private Page teamMenu(int idx) {
        List<String[]> t = teams();
        Page p = new Page(t.get(idx)[1]);
        if (idx > 0) p.action("نقل لفوق", () -> { Collections.swap(t, idx, idx - 1); saveTeams(t); popPage(); });
        if (idx < t.size() - 1) p.action("نقل لتحت", () -> { Collections.swap(t, idx, idx + 1); saveTeams(t); popPage(); });
        p.action("إزالة من فرقك", () -> { t.remove(idx); saveTeams(t); popPage(); });
        return p;
    }

    private Page leaguePage(String league, List<JSONObject> list) {
        Page p = new Page(leagueName(league));
        for (JSONObject t : list) {
            String id = t.optString("id");
            p.toggle(t.optString("ar", t.optString("en")), "", () -> hasTeam(id), on -> {
                if (on) addTeam(t); else removeTeam(id);
            });
        }
        return p;
    }

    private boolean hasTeam(String id) {
        for (String[] t : teams()) if (t[0].equals(id)) return true;
        return false;
    }

    private void addTeam(JSONObject t) {
        List<String[]> mine = teams();
        String id = t.optString("id");
        for (String[] x : mine) if (x[0].equals(id)) { toast("موجود في فرقك"); return; }
        if (mine.size() >= 10) { toast("فرقك ١٠ بالكثير"); return; }
        mine.add(new String[]{id, t.optString("ar", t.optString("en"))});
        saveTeams(mine);
        toast("أضفت " + t.optString("ar", t.optString("en")));
    }

    private void removeTeam(String id) {
        List<String[]> mine = teams();
        for (int i = mine.size() - 1; i >= 0; i--) if (mine.get(i)[0].equals(id)) mine.remove(i);
        saveTeams(mine);
    }

    private List<JSONObject> teamsCache;

    private List<JSONObject> loadTeams() {
        if (teamsCache != null) return teamsCache;
        teamsCache = new ArrayList<>();
        try (InputStream in = getAssets().open("data/teams.json")) {
            JSONArray a = new JSONArray(new String(readAll(in), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) teamsCache.add(a.getJSONObject(i));
        } catch (Exception ignored) { }
        return teamsCache;
    }

    private static boolean matches(JSONObject t, String q) {
        String n = norm(q);
        if (n.isEmpty()) return false;
        if (norm(t.optString("ar")).contains(n) || norm(t.optString("en")).contains(n)) return true;
        JSONArray alt = t.optJSONArray("alt");
        if (alt != null) for (int i = 0; i < alt.length(); i++) if (norm(alt.optString(i)).contains(n)) return true;
        return false;
    }

    /** Arabic and English loosely: no «ال», one alef, no taa marbuta, lower case, no dashes */
    private static String norm(String s) {
        s = s.toLowerCase(Locale.ROOT).replaceAll("[\\u064B-\\u065F]", "").replaceAll("[أإآ]", "ا").replace('ة', 'ه').replace('ى', 'ي');
        s = s.replaceAll("(^|\\s)ال", "$1").replaceAll("\\b(al|fc|club)\\b", "").replaceAll("[-'’.]", " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String league(JSONObject t) {
        JSONArray ls = t.optJSONArray("leagues");
        return ls != null && ls.length() > 0 ? leagueName(ls.optString(0)) : "";
    }

    private static String leagueName(String l) {
        switch (l) {
            case "Saudi-Arabian Pro League": return "دوري روشن السعودي";
            case "Saudi First Division League": return "دوري يلو";
            case "National teams": return "المنتخبات";
            case "English Premier League": return "الدوري الإنجليزي";
            case "Spanish La Liga": return "الدوري الإسباني";
            case "Italian Serie A": return "الدوري الإيطالي";
            case "German Bundesliga": return "الدوري الألماني";
            case "French Ligue 1": return "الدوري الفرنسي";
            case "Portuguese Primeira Liga": return "الدوري البرتغالي";
            case "Dutch Eredivisie": return "الدوري الهولندي";
            case "Turkish Super Lig": return "الدوري التركي";
            case "Egyptian Premier League": return "الدوري المصري";
            case "UAE Pro League": return "الدوري الإماراتي";
            case "Qatar Stars League": return "الدوري القطري";
            case "American Major League Soccer": return "الدوري الأمريكي";
            default: return l;
        }
    }

    private Page cityPage() {
        Page p = new Page("المدينة");
        note("المدينة", "لمواقيت الصلاة والطقس.");
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        List<String[]> cities = new ArrayList<>();   // [name, region]
        EditText search = p.search("ابحث عن مدينة", q -> {
            results.removeAllViews();
            int n = 0;
            for (String[] c : cities) {
                if (!q.trim().isEmpty() && !norm(c[0]).contains(norm(q))) continue;
                results.addView(p.makeLink(c[0], c[1], "", () -> { setSaver("city", c[0]); popPage(); }));
                if (++n >= 8) break;
            }
            p.relink();
        });
        p.add(results);
        broadcast("sa.urtv.screensaver.CITIES", null, data -> {
            if (data == null) return;
            for (String line : data.split("\n")) {
                int c = line.indexOf(':');
                if (c < 0) continue;
                String region = line.substring(0, c).trim();
                for (String name : line.substring(c + 1).split("،")) if (!name.trim().isEmpty()) cities.add(new String[]{name.trim(), region});
            }
            search.setText(search.getText());   // fill the list
        });
        return p;
    }

    // ---------------------------------------------------------------- favourites

    private void dockPage(Page p) {
        note("المفضلة", "اضغط على تطبيق لنقله أو إزالته. الأول في القائمة هو الأول من اليمين في الشريط.");
        Map<String, String> apps = Dock.launchable(this);
        List<String> row = Dock.shown(this, apps);
        for (int i = 0; i < row.size(); i++) {
            final int idx = i;
            String pkg = row.get(i);
            p.link(apps.containsKey(pkg) ? apps.get(pkg) : pkg, idx == 0 ? "الأول" : "", "", () -> pushPage(dockMenu(idx), true));
        }
        if (row.size() < Dock.MAX) p.link("إضافة تطبيق", "", "", () -> pushPage(addAppPage(), true));
    }

    private Page dockMenu(int idx) {
        Map<String, String> apps = Dock.launchable(this);
        List<String> row = Dock.shown(this, apps);
        Page p = new Page(apps.containsKey(row.get(idx)) ? apps.get(row.get(idx)) : row.get(idx));
        if (idx > 0) p.action("نقل لليمين (قبل)", () -> { Collections.swap(row, idx, idx - 1); Dock.save(this, row); popPage(); });
        if (idx < row.size() - 1) p.action("نقل لليسار (بعد)", () -> { Collections.swap(row, idx, idx + 1); Dock.save(this, row); popPage(); });
        if (row.size() > Dock.MIN) p.action("إزالة من المفضلة", () -> { row.remove(idx); Dock.save(this, row); popPage(); });
        return p;
    }

    private Page addAppPage() {
        Page p = new Page("إضافة تطبيق");
        Map<String, String> apps = Dock.launchable(this);
        List<String> row = Dock.shown(this, apps);
        List<Map.Entry<String, String>> rest = new ArrayList<>();
        for (Map.Entry<String, String> e : apps.entrySet()) if (!row.contains(e.getKey())) rest.add(e);
        Collections.sort(rest, (a, b) -> a.getValue().compareToIgnoreCase(b.getValue()));
        for (Map.Entry<String, String> e : rest)
            p.link(e.getValue(), "", "", () -> { row.add(e.getKey()); Dock.save(this, row); popPage(); });
        return p;
    }

    // ---------------------------------------------------------------- notifications

    private void notifyPage(Page p) {
        boolean has = installed("sa.urtv.notify");
        note("التنبيهات", has ? "Home Assistant يرسل للتلفزيون على المنفذ 7676 (Notifications for Android TV)، وبث الكاميرا على 7979."
                : "«تنبيهات تلفزيونك» مو مركّب: optimize/install.sh notify");
        p.info("تنبيهات تلفزيونك", has ? "مركّب ويستقبل" : "غير مركّب");
        if (!has) return;
        p.action("أرسل تنبيه تجريبي", () -> new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:7676/").openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream o = c.getOutputStream()) {
                    o.write(("title=" + Uri.encode("تنبيه تجريبي") + "&msg=" + Uri.encode("من إعدادات تلفزيونك") + "&duration=8")
                            .getBytes(StandardCharsets.UTF_8));
                }
                c.getResponseCode();
            } catch (Exception ignored) { }
        }).start());
        p.link("صفحة التطبيق", "", "", () -> open(new Intent().setClassName("sa.urtv.notify", "sa.urtv.notify.MainActivity")));
    }

    // ---------------------------------------------------------------- system

    private void systemPage(Page p) {
        note("النظام", "");
        p.info("الجهاز", cap(Build.MANUFACTURER) + " " + Build.MODEL);
        p.info("أندرويد", Build.VERSION.RELEASE + (Build.VERSION.SDK_INT >= 23 ? " · تحديث أمني " + Build.VERSION.SECURITY_PATCH : ""));
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        p.info("المساحة المتاحة", String.format(Locale.US, "%.1f من %.1f جيجا", fs.getAvailableBytes() / 1e9, fs.getTotalBytes() / 1e9));
        p.link("التطبيقات", "", "الإذونات، والمساحة، والإيقاف", () -> system(new Intent(Settings.ACTION_APPLICATION_SETTINGS)));
        p.link("التاريخ والوقت", java.util.TimeZone.getDefault().getID(), "", () -> system(new Intent(Settings.ACTION_DATE_SETTINGS)));
        p.link("اللغة", Locale.getDefault().getDisplayLanguage(new Locale("ar")), "", () -> system(new Intent(Settings.ACTION_LOCALE_SETTINGS)));
        p.link("حول الجهاز والتحديثات", "", "", () -> system(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)));
        p.link("كل إعدادات أندرويد", "", "القائمة الكاملة للنظام", () -> system(new Intent(Settings.ACTION_SETTINGS)));
    }

    // ================================================================ the screensaver's settings

    private void loadSaver(Runnable after) {
        broadcast("sa.urtv.screensaver.GET", null, data -> {
            Map<String, String> m = new HashMap<>();
            if (data != null) for (String line : data.split("\n")) {
                int c = line.indexOf(": ");
                if (c > 0) m.put(line.substring(0, c).trim(), line.substring(c + 2).trim());
            }
            saver = m;
            if (after != null) after.run();
        });
    }

    private void setSaver(String key, String value) {
        Bundle extras = new Bundle();
        extras.putString(key, value);
        broadcast("sa.urtv.screensaver.SET", extras, data -> {
            if (data != null && data.startsWith("error")) toast(data);
            loadSaver(() -> { refreshTop(); reloadPreview(); });
        });
    }

    private interface Result { void got(String data); }

    private void broadcast(String action, Bundle extras, Result r) {
        Intent i = new Intent(action).setComponent(new ComponentName(SAVER, SAVER + ".SaverAppReceiver"));
        if (extras != null) i.putExtras(extras);
        sendOrderedBroadcast(i, null, new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent in) { r.got(getResultData()); }
        }, main, 0, null, null);
    }

    // ---------------------------------------------------------------- preview

    @SuppressLint("SetJavaScriptEnabled")
    private void showPreview(boolean on) {
        previewBox.setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on) { if (preview != null) { previewBox.removeAllViews(); preview.destroy(); preview = null; } return; }
        if (preview == null) {
            preview = new WebView(this);
            preview.setFocusable(false);
            preview.setBackgroundColor(Color.BLACK);
            preview.getSettings().setJavaScriptEnabled(true);
            preview.getSettings().setDomStorageEnabled(true);
            preview.getSettings().setAllowFileAccess(true);
            preview.getSettings().setAllowFileAccessFromFileURLs(true);
            preview.getSettings().setAllowUniversalAccessFromFileURLs(true);
            float scale = dp(220) / 1920f;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1920, 1080);
            preview.setLayoutParams(lp);
            preview.setPivotX(0);
            preview.setPivotY(0);
            preview.setScaleX(scale);
            preview.setScaleY(scale);
            previewBox.addView(preview);
        }
        reloadPreview();
    }

    private void reloadPreview() {
        if (preview == null) return;
        String frag = "";
        try (Cursor c = getContentResolver().query(Uri.parse("content://sa.urtv.screensaver.config"), null, null, null, null)) {
            if (c != null && c.moveToFirst()) frag = c.getString(0);
        } catch (RuntimeException ignored) { }
        preview.loadUrl("file:///android_asset/web/tv.html#" + frag);
    }

    // ================================================================ pages

    /** true while pages swap: focus passing through the sections then must not switch the section */
    private boolean swapping;

    private void pushPage(Page p, boolean animate) {
        swapping = true;
        View old = stack.isEmpty() ? null : stack.get(stack.size() - 1);
        stack.add(p.view);
        middle.addView(p.view, new FrameLayout.LayoutParams(-1, -1));
        if (animate) {
            p.view.setAlpha(0f);
            p.view.setTranslationX(-dp(24));
            p.view.animate().alpha(1f).translationX(0).setDuration(300).setInterpolator(EASE).start();
        }
        // focus moves to the new page before the old one hides, so it never falls back to the first section
        if (old != null) { p.focusFirst(); old.setVisibility(View.GONE); }
        swapping = false;
    }

    private void popPage() {
        if (stack.size() <= 1) return;
        swapping = true;
        View top = stack.remove(stack.size() - 1);
        if (stack.size() == 1) {
            stack.add(top);           // replaceRoot keeps the root hidden while a page is above it
            replaceRoot();
            stack.remove(top);
            swapping = true;
        }
        View under = stack.get(stack.size() - 1);
        under.setVisibility(View.VISIBLE);
        Page p = (Page) under.getTag();
        if (stack.size() > 1) p.refreshValues();
        p.focusAt(p.lastFocus);
        middle.removeView(top);
        swapping = false;
    }

    /** values changed: the root page is built again, a sub page repaints its rows */
    private void refreshTop() {
        if (stack.isEmpty()) return;
        if (stack.size() == 1) replaceRoot();
        else {
            Page top = (Page) stack.get(stack.size() - 1).getTag();
            if (top.rebuild == null) { top.refreshValues(); return; }
            swapping = true;
            Page np = top.rebuild.get();
            np.lastFocus = top.lastFocus;
            middle.addView(np.view, new FrameLayout.LayoutParams(-1, -1));
            np.focusAt(top.lastFocus);
            stack.set(stack.size() - 1, np.view);
            middle.removeView(top.view);
            swapping = false;
        }
    }

    private boolean isInMiddle(View v) {
        for (android.view.ViewParent pv = v.getParent(); pv != null; pv = pv.getParent()) if (pv == middle) return true;
        return false;
    }

    private void focusMiddle() {
        if (stack.isEmpty()) return;
        ((Page) stack.get(stack.size() - 1).getTag()).focusFirst();
    }

    private interface Get<T> { T get(); }
    private interface Set<T> { void set(T v); }
    private interface Query { void changed(String q); }

    /** One column of rows: a title, then infos, switches, choices, a slider, links, a search box. */
    final class Page {
        final ScrollView view;
        final LinearLayout list;
        private final List<View> focusables = new ArrayList<>();
        private final List<Runnable> refreshers = new ArrayList<>();
        int lastFocus;
        /** a page whose rows depend on the values (the followed teams): built again instead of repainted */
        Get<Page> rebuild;

        Page(String title) {
            view = new ScrollView(TvSettings.this);
            view.setVerticalScrollBarEnabled(false);
            view.setTag(this);
            view.setSmoothScrollingEnabled(true);
            list = new LinearLayout(TvSettings.this);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(0, 0, 0, dp(40));
            view.addView(list, new ScrollView.LayoutParams(-1, -2));
            TextView t = text(title, 20, medium, TEXT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(46);
            lp.bottomMargin = dp(10);
            list.addView(t, lp);
        }

        void add(View v) { list.addView(v, new LinearLayout.LayoutParams(-1, -2)); }

        void header(String s) {
            TextView t = text(s, 13, medium, GOLD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(16);
            lp.bottomMargin = dp(6);
            list.addView(t, lp);
        }

        void info(String label, String value) { add(makeInfo(label, value)); }

        View makeInfo(String label, String value) {
            LinearLayout r = row(false);
            r.addView(text(value, 13, regular, SUB), new LinearLayout.LayoutParams(-2, -2));
            r.addView(text(label, 14, medium, TEXT), new LinearLayout.LayoutParams(0, -2, 1f));
            return r;
        }

        void link(String label, String value, String hint, Runnable onClick) { add(makeLink(label, value, hint, onClick)); }

        View makeLink(String label, String value, String hint, Runnable onClick) {
            LinearLayout r = row(true);
            ImageView chev = icon(R.drawable.ic_chevron_left, 0x66FFFFFF);
            r.addView(chev, new LinearLayout.LayoutParams(dp(16), dp(16)));
            TextView v = text(value, 13, regular, SUB);
            v.setSingleLine(true);
            v.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, -2, 1f);
            vp.leftMargin = dp(10);
            vp.rightMargin = dp(12);
            v.setGravity(Gravity.LEFT);
            r.addView(v, vp);
            r.addView(text(label, 14, medium, TEXT), new LinearLayout.LayoutParams(-2, -2));
            r.setOnClickListener(x -> onClick.run());
            hint(r, label, hint);
            return r;
        }

        void action(String label, Runnable onClick) {
            LinearLayout r = row(true);
            r.addView(text(label, 14, medium, TEXT), new LinearLayout.LayoutParams(-1, -2));
            r.setOnClickListener(x -> onClick.run());
            add(r);
        }

        void toggle(String label, String hint, Get<Boolean> get, Set<Boolean> set) {
            LinearLayout r = row(true);
            View sw = new View(TvSettings.this);
            GradientDrawable track = rounded(0, 10);
            sw.setBackground(track);
            View knob = new View(TvSettings.this);
            knob.setBackground(rounded(0, 8));
            FrameLayout swBox = new FrameLayout(TvSettings.this);
            swBox.addView(sw, new FrameLayout.LayoutParams(dp(36), dp(20), Gravity.CENTER));
            swBox.addView(knob, new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER_VERTICAL | Gravity.LEFT));
            r.addView(swBox, new LinearLayout.LayoutParams(dp(40), dp(24)));
            r.addView(text(label, 14, medium, TEXT), new LinearLayout.LayoutParams(0, -2, 1f));
            Runnable paint = () -> {
                boolean on = Boolean.TRUE.equals(get.get());
                track.setColor(on ? GOLD : 0xFF3A443F);
                ((GradientDrawable) knob.getBackground()).setColor(on ? 0xFF0B1512 : 0xFF8B9590);
                knob.animate().translationX(on ? dp(5) : dp(21)).setDuration(220).setInterpolator(EASE).start();
            };
            paint.run();
            refreshers.add(paint);
            r.setOnClickListener(x -> { set.set(!Boolean.TRUE.equals(get.get())); main.postDelayed(paint, 200); });
            hint(r, label, hint);
            add(r);
        }

        /** Left/Right step through the options (Left = the next one, as Arabic reads); OK steps too. */
        void choice(String label, String[] names, String[] values, Get<String> get, Set<String> set) {
            LinearLayout r = row(true);
            TextView val = text("", 13, medium, GOLD);
            val.setPadding(dp(10), dp(3), dp(10), dp(3));
            val.setBackground(rounded(0x18FFFFFF, 8));
            r.addView(val, new LinearLayout.LayoutParams(-2, -2));
            r.addView(text(label, 14, medium, TEXT), new LinearLayout.LayoutParams(0, -2, 1f));
            Runnable paint = () -> {
                String cur = get.get();
                String name = names[0];
                for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) name = names[i];
                val.setText("‹  " + name + "  ›");
            };
            paint.run();
            refreshers.add(paint);
            Set<Integer> step = d -> {
                int at = 0;
                String cur = get.get();
                for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) at = i;
                int next = Math.max(0, Math.min(values.length - 1, at + d));
                if (next != at) { set.set(values[next]); main.postDelayed(paint, 150); }
            };
            r.setOnKeyListener((v, code, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) { step.set(1); return true; }
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { step.set(-1); return true; }
                return false;
            });
            r.setOnClickListener(x -> {
                int at = 0;
                for (int i = 0; i < values.length; i++) if (values[i].equals(get.get())) at = i;
                set.set(values[(at + 1) % values.length]);
                main.postDelayed(paint, 150);
            });
            hint(r, label, "يمين ويسار يغيّرون الاختيار");
            add(r);
        }

        void slider(String label, int max, Get<Integer> get, Set<Integer> set) {
            LinearLayout r = row(true);
            FrameLayout bar = new FrameLayout(TvSettings.this);
            View track = new View(TvSettings.this);
            track.setBackground(rounded(0x26FFFFFF, 3));
            View fill = new View(TvSettings.this);
            fill.setBackground(rounded(GOLD, 3));
            bar.addView(track, new FrameLayout.LayoutParams(dp(160), dp(6), Gravity.CENTER_VERTICAL));
            FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(0, dp(6), Gravity.CENTER_VERTICAL | Gravity.LEFT);   // fills from the left like every TV: Right is louder
            bar.addView(fill, fp);
            TextView num = text("", 13, regular, SUB);
            r.addView(num, new LinearLayout.LayoutParams(dp(40), -2));
            r.addView(bar, new LinearLayout.LayoutParams(dp(160), dp(20)));
            r.addView(text(label, 14, medium, TEXT), new LinearLayout.LayoutParams(0, -2, 1f));
            Runnable paint = () -> {
                int v = get.get();
                fp.width = Math.round(dp(160) * v / (float) Math.max(1, max));
                fill.setLayoutParams(fp);
                num.setText(Math.round(100f * v / Math.max(1, max)) + "٪");
            };
            paint.run();
            refreshers.add(paint);
            r.setOnKeyListener((v, code, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                int d = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
                if (d == 0) return false;
                set.set(Math.max(0, Math.min(max, get.get() + d)));
                paint.run();
                return true;
            });
            hint(r, label, "يمين يرفع الصوت، ويسار ينزّله");
            add(r);
        }

        /** a search box already at the top: the TV keyboard opens on OK, results follow each letter */
        EditText search(String hint, Query q) {
            EditText e = new EditText(TvSettings.this);
            e.setHint(hint);
            e.setHintTextColor(0x80F6EEDC);
            e.setTextColor(TEXT);
            e.setTypeface(regular);
            e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            e.setSingleLine(true);
            e.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            e.setTextDirection(View.TEXT_DIRECTION_RTL);
            e.setInputType(InputType.TYPE_CLASS_TEXT);
            e.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            e.setPadding(dp(14), 0, dp(14), 0);
            GradientDrawable bg = rounded(0x1AFFFFFF, 10);
            bg.setStroke(dp(1), 0x33DCB86F);
            e.setBackground(bg);
            e.setOnFocusChangeListener((v, has) -> bg.setStroke(dp(1), has ? 0xCCDCB86F : 0x33DCB86F));
            e.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                public void onTextChanged(CharSequence s, int a, int b, int c) { }
                public void afterTextChanged(Editable s) { q.changed(s.toString()); }
            });
            e.setOnEditorActionListener((v, id, ev) -> {
                getSystemService(InputMethodManager.class).hideSoftInputFromWindow(v.getWindowToken(), 0);
                if (!focusables.isEmpty() && focusables.size() > 1) focusables.get(1).requestFocus();
                return true;
            });
            e.setId(View.generateViewId());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(40));
            lp.bottomMargin = dp(8);
            list.addView(e, lp);
            focusables.add(e);
            return e;
        }

        /** after results changed: wire Up/Down again over every focusable row */
        void relink() {
            focusables.clear();
            collect(list);
            link();
        }

        private void collect(ViewGroup g) {
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c.isFocusable() && c.getVisibility() == View.VISIBLE) focusables.add(c);
                else if (c instanceof ViewGroup) collect((ViewGroup) c);
            }
        }

        private void link() {
            for (int i = 0; i < focusables.size(); i++) {
                View v = focusables.get(i);
                v.setNextFocusUpId(focusables.get(Math.max(0, i - 1)).getId());
                v.setNextFocusDownId(focusables.get(Math.min(focusables.size() - 1, i + 1)).getId());
                if (!(v instanceof EditText)) v.setNextFocusLeftId(v.getId());
                v.setNextFocusRightId(sectionViews.get(section).getId());
            }
        }

        void focusFirst() {
            relink();
            if (!focusables.isEmpty()) focusables.get(0).requestFocus();
        }

        void focusAt(int i) {
            relink();
            if (!focusables.isEmpty()) focusables.get(Math.max(0, Math.min(focusables.size() - 1, i))).requestFocus();
        }

        int indexOf(View v) { relink(); return focusables.indexOf(v); }

        void refreshValues() { for (Runnable r : refreshers) r.run(); }

        private LinearLayout row(boolean focusable) {
            LinearLayout r = new LinearLayout(TvSettings.this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(14), 0, dp(14), 0);
            r.setMinimumHeight(dp(focusable ? 40 : 32));
            GradientDrawable bg = rounded(focusable ? ROW : 0, 10);
            r.setBackground(bg);
            if (focusable) {
                r.setFocusable(true);
                r.setId(View.generateViewId());
                r.setOnFocusChangeListener((v, has) -> {
                    bg.setColor(has ? 0x26DCB86F : ROW);
                    bg.setStroke(dp(1), has ? 0xCCDCB86F : 0);
                    if (has) { keepVisible(v); lastFocus = focusables.indexOf(v); }
                });
                focusables.add(r);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(focusable ? 4 : 0);
            r.setLayoutParams(lp);
            link();
            return r;
        }

        /** the focused row in the middle third; at the top, the page's title (and a header above) stays in view */
        private void keepVisible(View v) {
            int y = 0;
            for (View c = v; c != list && c != null; c = (View) c.getParent()) y += c.getTop();
            int target = Math.max(0, y - view.getHeight() / 3);
            if (focusables.indexOf(v) <= 1) target = 0;
            final int to = target;
            view.post(() -> view.smoothScrollTo(0, to));   // after the ScrollView's own scroll to the focused row
        }

        private void hint(View r, String label, String hint) {
            View.OnFocusChangeListener before = r.getOnFocusChangeListener();
            r.setOnFocusChangeListener((v, has) -> {
                if (before != null) before.onFocusChange(v, has);
                if (has && hint != null && !hint.isEmpty() && section != SECTION_SAVER) note(label, hint);
            });
        }
    }

    // ================================================================ helpers

    private void system(Intent i) {
        // a screen two settings apps answer would ask «open with»: Android TV's own
        if (i.getComponent() == null && i.getPackage() == null && i.getAction() != null && i.getAction().startsWith("android.settings")) {
            Intent tv = new Intent(i).setPackage("com.android.tv.settings");
            if (tv.resolveActivity(getPackageManager()) != null) i = tv;
        }
        open(i);
    }

    private void open(Intent i) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (ActivityNotFoundException | SecurityException e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (ActivityNotFoundException ignored) { }
        }
    }

    private boolean installed(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; }
        catch (android.content.pm.PackageManager.NameNotFoundException e) { return false; }
    }

    private void toast(String s) { android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show(); }

    private static String bars(int level) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 4; i++) b.append(i < level ? '●' : '○');
        return b.toString();
    }

    private static String cap(String s) { return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1); }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
        return o.toByteArray();
    }

    private TextView text(String s, float sp, Typeface tf, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setGravity(Gravity.RIGHT);
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
        return t;
    }

    private ImageView icon(int res, int tint) {
        ImageView iv = new ImageView(this);
        Drawable d = getDrawable(res).mutate();
        d.setTint(tint);
        iv.setImageDrawable(d);
        return iv;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(radiusDp));
        g.setColor(color);
        return g;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
