package sa.urtv.home;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * «إعدادات تلفزيونك»: the TV's everyday settings in Arabic, as a panel that slides in from the right over the
 * screen (the side Arabic starts from), in the home screen's style. Each row opens the system screen for it;
 * the last one opens Android's full settings. The top bar's settings button opens it.
 */
public class SettingsPanel extends Activity {
    private static final int GOLD = 0xFFD6AF6B, TEXT = 0xFFF6EEDC, SUB = 0xFF9C9279;
    private static final Interpolator EASE = new PathInterpolator(0.32f, 0.72f, 0f, 1f);
    private Typeface regular, medium, display;
    private final List<View> rows = new ArrayList<>();
    private LinearLayout panel;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            regular = Typeface.createFromAsset(getAssets(), "fonts/Thmanyah-Regular.otf");
            medium = Typeface.createFromAsset(getAssets(), "fonts/Thmanyah-Medium.otf");
            display = Typeface.createFromAsset(getAssets(), "fonts/ThmanyahDisplay-Bold.otf");
        } catch (RuntimeException e) { regular = Typeface.DEFAULT; medium = display = Typeface.DEFAULT_BOLD; }

        Window w = getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setDimAmount(0.45f);

        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(v -> close());
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(30), dp(40), dp(30), dp(28));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{0xF20A1410, 0xEB0A1410});
        panel.setBackground(bg);
        root.addView(panel, new FrameLayout.LayoutParams(dp(470), -1, Gravity.RIGHT));

        TextView title = text("الإعدادات", 30, display, TEXT);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView sub = text(Build.MANUFACTURER.substring(0, 1).toUpperCase(Locale.ROOT) + Build.MANUFACTURER.substring(1)
                + " " + Build.MODEL + " · أندرويد " + Build.VERSION.RELEASE, 13, regular, SUB);
        panel.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(18), 0, dp(18));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        section(list, "الاتصال");
        row(list, R.drawable.ic_wifi, "الواي فاي", wifiName(), new Intent(Settings.ACTION_WIFI_SETTINGS));
        row(list, R.drawable.ic_bluetooth, "البلوتوث والأجهزة", bluetooth(), new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        section(list, "الصورة والصوت");
        row(list, R.drawable.ic_display, "الصورة والعرض", resolution(), new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        row(list, R.drawable.ic_volume, "الصوت", volume(), new Intent(Settings.ACTION_SOUND_SETTINGS));
        section(list, "تلفزيونك");
        row(list, R.drawable.ic_screensaver, "شاشة التوقف", "المدينة، الفرق، البطاقة جنب الساعة",
                new Intent().setClassName(MainActivity.SAVER_PKG, MainActivity.SAVER_PKG + ".SettingsActivity"));
        row(list, R.drawable.ic_star, "ترتيب المفضلة", "التطبيقات في الشريط وترتيبها", new Intent(this, DockActivity.class));
        row(list, R.drawable.ic_bell, "تنبيهات تلفزيونك", "تنبيهات Home Assistant والكاميرات",
                new Intent().setClassName("sa.urtv.notify", "sa.urtv.notify.MainActivity"));
        section(list, "النظام");
        row(list, R.drawable.ic_apps, "التطبيقات", "الإذونات، المساحة، الإيقاف", new Intent(Settings.ACTION_APPLICATION_SETTINGS));
        row(list, R.drawable.ic_time, "التاريخ والوقت", java.util.TimeZone.getDefault().getDisplayName(new Locale("ar")),
                new Intent(Settings.ACTION_DATE_SETTINGS));
        row(list, R.drawable.ic_language, "اللغة", Locale.getDefault().getDisplayLanguage(new Locale("ar")),
                new Intent(Settings.ACTION_LOCALE_SETTINGS));
        row(list, R.drawable.ic_info, "حول الجهاز والتحديثات", "أندرويد " + Build.VERSION.RELEASE,
                new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
        row(list, R.drawable.ic_tune, "كل إعدادات أندرويد", "القائمة الكاملة للنظام", new Intent(Settings.ACTION_SETTINGS));

        for (int i = 0; i < rows.size(); i++) {   // Up/Down only, and no wrapping out of the panel
            View r = rows.get(i);
            r.setNextFocusUpId(rows.get(Math.max(0, i - 1)).getId());
            r.setNextFocusDownId(rows.get(Math.min(rows.size() - 1, i + 1)).getId());
            r.setNextFocusLeftId(r.getId());
            r.setNextFocusRightId(r.getId());
        }
        setContentView(root);
        rows.get(0).requestFocus();

        // in from the right: slide and fade on the shared ease
        panel.setTranslationX(dp(80));
        panel.setAlpha(0f);
        panel.animate().translationX(0).alpha(1f).setDuration(420).setInterpolator(EASE).start();
    }

    @Override
    public void onBackPressed() { close(); }

    private void close() {
        panel.animate().translationX(dp(80)).alpha(0f).setDuration(260).setInterpolator(EASE)
                .withEndAction(() -> { finish(); overridePendingTransition(0, 0); }).start();
    }

    private void section(LinearLayout list, String name) {
        TextView t = text(name, 13, medium, GOLD);
        t.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = list.getChildCount() == 0 ? 0 : dp(16);
        p.bottomMargin = dp(6);
        list.addView(t, p);
    }

    /** One row: icon at the right, name and current value, a chevron at the left (where it leads). */
    private void row(LinearLayout list, int icon, String name, String value, Intent target) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        r.setMinimumHeight(dp(64));
        r.setFocusable(true);
        r.setId(View.generateViewId());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0x00000000);
        bg.setStroke(dp(1), 0x00000000);
        r.setBackground(bg);

        ImageView chev = icon(R.drawable.ic_chevron_left, 18, 0x66FFFFFF);
        r.addView(chev, new LinearLayout.LayoutParams(dp(18), dp(18)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView n = text(name, 17, medium, TEXT);
        texts.addView(n);
        TextView v = text(value == null || value.isEmpty() ? " " : value, 13, regular, SUB);
        v.setIncludeFontPadding(true);
        v.setPadding(0, dp(2), 0, dp(2));
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(v);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.leftMargin = dp(12);
        tp.rightMargin = dp(14);
        r.addView(texts, tp);
        ImageView ic = icon(icon, 22, GOLD);
        r.addView(ic, new LinearLayout.LayoutParams(dp(22), dp(22)));

        r.setOnFocusChangeListener((view, has) -> {
            bg.setColor(has ? 0x1FDCB86F : 0x00000000);
            bg.setStroke(dp(1), has ? 0xCCDCB86F : 0x00000000);
            chev.getDrawable().setTint(has ? GOLD : 0x66FFFFFF);
            view.animate().translationX(has ? -dp(4) : 0).setDuration(260).setInterpolator(EASE).start();
        });
        r.setOnClickListener(view -> open(target));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = dp(4);
        list.addView(r, rp);
        rows.add(r);
    }

    private void open(Intent i) {
        // a system screen that two settings apps answer would ask «open with»: prefer Android TV's own
        if (i.getComponent() == null && i.getPackage() == null && i.getAction() != null && i.getAction().startsWith("android.settings")) {
            Intent tv = new Intent(i).setPackage("com.android.tv.settings");
            if (tv.resolveActivity(getPackageManager()) != null) i = tv;
        }
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            // some TVs have no screen for that setting: Android's full settings instead
            try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (ActivityNotFoundException ignored) { }
        }
        finish();
        overridePendingTransition(0, 0);
    }

    // ---------------------------------------------------------------- current values

    private String wifiName() {
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc == null) return "غير متصل";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "متصل بالكيبل";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                String s = wm == null || wm.getConnectionInfo() == null ? null : wm.getConnectionInfo().getSSID();
                if (s != null && !s.contains("unknown ssid")) return s.replace("\"", "");
                return "متصل";
            }
        } catch (SecurityException ignored) { }
        return "متصل";
    }

    @SuppressWarnings("deprecation")
    private String bluetooth() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null) return "غير متوفر";
            return a.isEnabled() ? "شغّال" : "مطفي";
        } catch (SecurityException e) { return "الريموت والسماعات"; }
    }

    private String resolution() {
        Display d = getWindowManager().getDefaultDisplay();
        Display.Mode m = d.getMode();
        int h = Math.min(m.getPhysicalHeight(), m.getPhysicalWidth());
        String name = h >= 2160 ? "4K" : h >= 1080 ? "Full HD" : h >= 720 ? "HD" : h + "p";
        return name + " · " + Math.round(m.getRefreshRate()) + " هرتز";
    }

    private String volume() {
        AudioManager am = getSystemService(AudioManager.class);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), now = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        return "مستوى الصوت " + Math.round(100f * now / Math.max(1, max)) + "٪";
    }

    // ---------------------------------------------------------------- views

    private TextView text(String s, float sp, Typeface tf, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setGravity(Gravity.RIGHT);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        return t;
    }

    private ImageView icon(int res, int sizeDp, int tint) {
        ImageView iv = new ImageView(this);
        Drawable d = getDrawable(res).mutate();
        d.setTint(tint);
        iv.setImageDrawable(d);
        return iv;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
