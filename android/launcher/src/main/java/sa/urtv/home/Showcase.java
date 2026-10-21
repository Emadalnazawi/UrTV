package sa.urtv.home;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What the focused favourite publishes for the home screen, shown in the screen's top part: «تابع المشاهدة»
 * first, then the app's own rows (Shahid's series, stc tv's picks…), as posters you can open straight away.
 * The data is the TV's program database (TvContract: watch_next_program, preview_program), readable with
 * READ_TV_LISTINGS; an app that publishes nothing keeps the normal home screen.
 */
final class Showcase {
    static final class Item {
        String pkg, title, detail, group, poster, wide, intent;
        float aspect = 16 / 9f;
        int progress = -1;   // percent watched, «watch next» only
    }

    private static final Interpolator EASE = new PathInterpolator(0.32f, 0.72f, 0f, 1f);
    private static final int MAX_ITEMS = 12;
    private static final long FRESH_MS = 2 * 60 * 1000;

    private final MainActivity m;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> images = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private final LinearLayout panel, row;
    private final HorizontalScrollView rowScroll;
    private final TextView group, detail;
    private final FrameLayout backdrop;
    private final ImageView[] backs = new ImageView[2];
    private int backFront = 0;
    private String backUrl;

    private volatile Map<String, List<Item>> data = new HashMap<>();
    private long loadedAt;
    private String shownPkg;
    private final List<View> posters = new ArrayList<>();
    private final Runnable backdropLater = this::backdropFromFocus;

    Showcase(MainActivity m, FrameLayout hero, FrameLayout.LayoutParams where, FrameLayout backdrop) {
        this.m = m;
        this.backdrop = backdrop;
        backdrop.setBackgroundColor(0xFF000000);
        backdrop.setAlpha(0f);
        for (int i = 0; i < 2; i++) {
            ImageView iv = new ImageView(m);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setAlpha(0f);
            backdrop.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            backs[i] = iv;
        }

        panel = new LinearLayout(m);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.RIGHT);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setAlpha(0f);
        panel.setVisibility(View.GONE);
        group = m.text(13, "medium", MainActivity.GOLD);
        group.setGravity(Gravity.RIGHT);
        group.setSingleLine(true);
        panel.addView(group, new LinearLayout.LayoutParams(-1, -2));
        detail = m.text(18, "medium", 0xFFFFFDF6);
        detail.setGravity(Gravity.RIGHT);
        detail.setSingleLine(true);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detail.setShadowLayer(m.dp(3), 0, m.dp(1), 0x80000000);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.bottomMargin = m.dp(12);
        panel.addView(detail, dl);

        rowScroll = new HorizontalScrollView(m) {
            // the row scrolls only through glideTo: Android's own jump to the focused poster made posters
            // appear from nowhere at the screen's edge when the remote was pressed fast
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect r) { return 0; }
        };
        rowScroll.setHorizontalScrollBarEnabled(false);
        rowScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rowScroll.setClipChildren(false);
        rowScroll.setClipToPadding(false);
        rowScroll.setSmoothScrollingEnabled(true);
        rowScroll.setPadding(m.dp(12), m.dp(10), m.dp(12), m.dp(12));
        row = new LinearLayout(m);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        rowScroll.addView(row, new FrameLayout.LayoutParams(-2, -2));
        panel.addView(rowScroll, new LinearLayout.LayoutParams(-1, -2));
        hero.addView(panel, where);
    }

    // ---------------------------------------------------------------- data

    /** Reads the database again (in the background) if it is older than two minutes, or when forced. */
    void refresh(boolean force) {
        if (!force && System.currentTimeMillis() - loadedAt < FRESH_MS) return;
        loadedAt = System.currentTimeMillis();
        ContentResolver cr = m.getContentResolver();
        pool.execute(() -> {
            Map<String, List<Item>> d = load(cr);
            main.post(() -> {
                data = d;
                m.showcaseReady();
            });
        });
    }

    boolean has(String pkg) {
        List<Item> l = data.get(pkg);
        return l != null && !l.isEmpty();
    }

    private static Map<String, List<Item>> load(ContentResolver cr) {
        Map<String, List<Item>> out = new HashMap<>();
        Map<Long, String> channels = new HashMap<>();
        try (Cursor c = cr.query(Uri.parse("content://android.media.tv/channel"),
                new String[]{"_id", "display_name"}, null, null, null)) {
            while (c != null && c.moveToNext()) channels.put(c.getLong(0), c.getString(1));
        } catch (RuntimeException ignored) { }

        String[] common = {"package_name", "title", "episode_title", "season_display_number", "episode_display_number",
                "poster_art_uri", "thumbnail_uri", "intent_uri", "poster_art_aspect_ratio"};
        try (Cursor c = cr.query(Uri.parse("content://android.media.tv/watch_next_program"),
                concat(common, "last_playback_position_millis", "duration_millis"), null, null,
                "last_engagement_time_utc_millis DESC")) {
            while (c != null && c.moveToNext()) {
                Item it = item(c, "تابع المشاهدة");
                long pos = c.getLong(9), dur = c.getLong(10);
                if (dur > 0 && pos > 0) {
                    it.progress = (int) Math.min(100, pos * 100 / dur);
                    long left = (dur - pos) / 60000;
                    if (left > 0) it.detail = it.detail + " — بقي " + MainActivity.arabicDigits(String.valueOf(left)) + " دقيقة";
                }
                add(out, it);
            }
        } catch (RuntimeException ignored) { }
        try (Cursor c = cr.query(Uri.parse("content://android.media.tv/preview_program"),
                concat(common, "channel_id"), null, null, "channel_id, weight DESC")) {
            while (c != null && c.moveToNext()) {
                String g = channels.get(c.getLong(9));
                add(out, item(c, g == null || g.trim().isEmpty() ? "" : g.trim()));
            }
        } catch (RuntimeException ignored) { }
        return out;
    }

    private static String[] concat(String[] a, String... b) {
        String[] r = new String[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static Item item(Cursor c, String group) {
        Item it = new Item();
        it.pkg = c.getString(0);
        it.title = nz(c.getString(1));
        String ep = nz(c.getString(2)), season = nz(c.getString(3)), num = nz(c.getString(4));
        StringBuilder d = new StringBuilder(it.title);
        if (!season.isEmpty()) d.append(" · الموسم ").append(MainActivity.arabicDigits(season));
        if (!num.isEmpty()) d.append(season.isEmpty() ? " · " : "، ").append("الحلقة ").append(MainActivity.arabicDigits(num));
        if (!ep.isEmpty() && !ep.equals(it.title)) d.append(" — ").append(ep);
        it.detail = d.toString();
        it.poster = c.getString(5);
        it.wide = c.getString(6);
        if (it.wide == null) it.wide = it.poster;
        if (it.poster == null) it.poster = it.wide;
        it.intent = c.getString(7);
        it.aspect = c.isNull(8) ? 16 / 9f : aspect(c.getInt(8));
        it.group = group;
        return it;
    }

    /** TvContract's poster aspect ratios: 16:9, 3:2, 4:3, 1:1, 2:3, movie poster */
    private static float aspect(int code) {
        switch (code) {
            case 1: return 3 / 2f;
            case 2: return 4 / 3f;
            case 3: return 1f;
            case 4: return 2 / 3f;
            case 5: return 1 / 1.441f;
            default: return 16 / 9f;
        }
    }

    private static void add(Map<String, List<Item>> out, Item it) {
        if (it.pkg == null || it.title.isEmpty()) return;
        List<Item> l = out.get(it.pkg);
        if (l == null) out.put(it.pkg, l = new ArrayList<>());
        if (l.size() >= MAX_ITEMS) return;
        for (Item x : l) if (x.title.equals(it.title)) return;   // the same show on two rows
        l.add(it);
    }

    private static String nz(String s) { return s == null ? "" : s.trim(); }

    // ---------------------------------------------------------------- showing

    boolean isShown() { return shownPkg != null; }

    boolean owns(View v) { return posters.contains(v); }

    int firstPosterId() { return posters.isEmpty() ? View.NO_ID : posters.get(0).getId(); }

    /** The app's posters in the top part; false (and hidden) when it publishes nothing. */
    boolean show(String pkg) {
        if (!has(pkg)) { hide(true); return false; }
        if (pkg.equals(shownPkg)) return true;
        boolean was = shownPkg != null;
        shownPkg = pkg;
        build(data.get(pkg));
        panel.setVisibility(View.VISIBLE);
        panel.animate().cancel();
        if (was) {
            // another app: the posters slide in again from a little lower
            panel.setAlpha(0f);
            panel.setTranslationY(m.dp(10));
        } else if (panel.getAlpha() == 0f) {
            panel.setTranslationY(m.dp(16));
        }
        panel.animate().alpha(1f).translationY(0).setStartDelay(was ? 40 : 120).setDuration(460).setInterpolator(EASE).start();
        backdrop.animate().cancel();
        backdrop.animate().alpha(1f).setStartDelay(0).setDuration(600).setInterpolator(EASE).start();
        List<Item> l = data.get(pkg);
        setBackdrop(l.get(0).wide);
        return true;
    }

    void hide(boolean animate) {
        if (shownPkg == null && panel.getVisibility() == View.GONE) return;
        shownPkg = null;
        main.removeCallbacks(backdropLater);
        panel.animate().cancel();
        backdrop.animate().cancel();
        if (!animate) {
            panel.setAlpha(0f);
            panel.setVisibility(View.GONE);
            backdrop.setAlpha(0f);
            return;
        }
        panel.animate().alpha(0f).translationY(m.dp(10)).setStartDelay(0).setDuration(260).setInterpolator(EASE)
                .withEndAction(() -> { if (shownPkg == null) panel.setVisibility(View.GONE); }).start();
        backdrop.animate().alpha(0f).setStartDelay(0).setDuration(420).setInterpolator(EASE).start();
    }

    /** the poster row of one app; MainActivity wires the remote's Up/Down to and from it */
    private void build(List<Item> items) {
        row.removeAllViews();
        posters.clear();
        int h = m.dp(118);
        // laid out right to left by hand (the app doesn't declare supportsRtl): the first item at the right end
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            View p = poster(it, h);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(h * it.aspect), h);
            lp.leftMargin = i == 0 ? 0 : m.dp(24);   // room for the focused poster to grow without covering its neighbours
            row.addView(p, 0, lp);
            posters.add(p);
        }
        for (int i = 0; i < posters.size(); i++) {
            View p = posters.get(i);
            p.setNextFocusRightId(i == 0 ? p.getId() : posters.get(i - 1).getId());
            p.setNextFocusLeftId(i == posters.size() - 1 ? p.getId() : posters.get(i + 1).getId());
        }
        Item first = items.get(0);
        group.setText(first.group.isEmpty() ? "" : first.group);
        detail.setText(first.detail);
        rowScroll.post(() -> rowScroll.scrollTo(row.getWidth(), 0));   // start at the right end
    }

    private View poster(Item it, int h) {
        FrameLayout f = new FrameLayout(m);
        f.setId(View.generateViewId());
        f.setFocusable(true);
        f.setClickable(true);
        f.setTag(it);
        f.setClipToOutline(true);
        f.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), m.dp(8)); }
        });
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF22252B);
        f.setBackground(bg);

        TextView name = m.text(12, "medium", 0xFFFFFDF6);
        name.setText(it.title);
        name.setGravity(Gravity.CENTER);
        name.setPadding(m.dp(8), 0, m.dp(8), 0);
        f.addView(name, new FrameLayout.LayoutParams(-1, -1));   // until the picture arrives, or if it doesn't

        ImageView img = new ImageView(m);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setAlpha(0f);
        f.addView(img, new FrameLayout.LayoutParams(-1, -1));
        loadInto(it.poster, img, Math.round(h * it.aspect), h);

        if (it.progress >= 0) {
            View track = new View(m);
            track.setBackgroundColor(0x66000000);
            f.addView(track, new FrameLayout.LayoutParams(-1, m.dp(4), Gravity.BOTTOM));
            View done = new View(m);
            done.setBackgroundColor(MainActivity.GOLD);
            FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(0, m.dp(4), Gravity.BOTTOM | Gravity.RIGHT);
            f.addView(done, dp);
            f.post(() -> { dp.width = f.getWidth() * it.progress / 100; done.setLayoutParams(dp); });
        }

        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(m.dp(8));
        ring.setStroke(m.dp(2), 0xE6FFFFFF);
        View shine = new View(m);
        shine.setBackground(ring);
        shine.setAlpha(0f);
        f.addView(shine, new FrameLayout.LayoutParams(-1, -1));

        f.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).translationZ(has ? m.dp(10) : 0)
                    .setDuration(has ? 300 : 200).setInterpolator(EASE).start();
            shine.animate().alpha(has ? 1f : 0f).setDuration(160).start();
            if (!has) return;
            glideTo(v);
            group.setText(it.group);
            detail.setText(it.detail);
            main.removeCallbacks(backdropLater);
            main.postDelayed(backdropLater, 280);   // passing quickly over posters doesn't flash the backdrop
        });
        f.setOnClickListener(v -> m.openItem(it));
        return f;
    }

    private android.animation.ObjectAnimator glide;

    /** the focused poster fully in view, with the next one peeking, in one eased move (presses in a row merge) */
    private void glideTo(View p) {
        int pad = m.dp(56), w = rowScroll.getWidth(), x = rowScroll.getScrollX();
        if (glide != null && glide.isRunning()) x = (Integer) glide.getAnimatedValue();
        int left = p.getLeft() - pad, right = p.getRight() + pad - w;
        int to = left < x ? left : right > x ? right : x;
        to = Math.max(0, Math.min(to, row.getWidth() - w + rowScroll.getPaddingLeft() + rowScroll.getPaddingRight()));
        if (glide != null) glide.cancel();
        if (to == rowScroll.getScrollX()) return;
        glide = android.animation.ObjectAnimator.ofInt(rowScroll, "scrollX", rowScroll.getScrollX(), to);
        glide.setDuration(420);
        glide.setInterpolator(EASE);
        glide.start();
    }

    private void backdropFromFocus() {
        View f = m.getCurrentFocus();
        if (f != null && owns(f)) setBackdrop(((Item) f.getTag()).wide);
    }

    private void setBackdrop(String url) {
        if (url == null || url.equals(backUrl)) return;
        backUrl = url;
        int w = m.getResources().getDisplayMetrics().widthPixels / 2;   // shown dimmed and big: half size is plenty
        fetch(url, w, w * 9 / 16, bm -> {
            if (!url.equals(backUrl)) return;
            ImageView next = backs[1 - backFront], cur = backs[backFront];
            next.setImageBitmap(bm);
            next.animate().cancel();
            next.animate().alpha(0.62f).setDuration(700).setInterpolator(EASE).start();
            cur.animate().alpha(0f).setDuration(700).setInterpolator(EASE).start();
            backFront = 1 - backFront;
        });
    }

    // ---------------------------------------------------------------- pictures

    private interface Got { void got(Bitmap b); }

    private void loadInto(String url, ImageView iv, int w, int h) {
        if (url == null) return;
        fetch(url, w, h, bm -> {
            iv.setImageBitmap(bm);
            iv.animate().alpha(1f).setDuration(260).start();
        });
    }

    private void fetch(String url, int w, int h, Got got) {
        String key = url + "@" + w;
        Bitmap cached = images.get(key);
        if (cached != null) { got.got(cached); return; }
        Context c = m.getApplicationContext();
        pool.execute(() -> {
            Bitmap bm = decode(c, url, w, h);
            if (bm == null) return;
            images.put(key, bm);
            main.post(() -> got.got(bm));
        });
    }

    private static Bitmap decode(Context c, String url, int w, int h) {
        try {
            byte[] bytes;
            if (url.startsWith("http")) {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(6000);
                con.setReadTimeout(8000);
                try (InputStream in = con.getInputStream()) { bytes = readAll(in); }
            } else {
                try (InputStream in = c.getContentResolver().openInputStream(Uri.parse(url))) { bytes = readAll(in); }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            int s = 1;
            while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        if (in == null) throw new java.io.IOException("no stream");
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        for (int n; (n = in.read(buf)) > 0; ) {
            o.write(buf, 0, n);
            if (o.size() > 8 * 1024 * 1024) throw new java.io.IOException("too large");
        }
        return o.toByteArray();
    }

    /** Up from a poster goes to the top bar, Down back to the favourite they belong to. */
    void wire(int up, int down) {
        for (View p : posters) { p.setNextFocusUpId(up); p.setNextFocusDownId(down); }
    }

    void destroy() { pool.shutdownNow(); }

    static Intent parse(Item it) {
        if (it.intent == null) return null;
        try { return Intent.parseUri(it.intent, Intent.URI_INTENT_SCHEME); }
        catch (Exception e) { return null; }
    }
}
