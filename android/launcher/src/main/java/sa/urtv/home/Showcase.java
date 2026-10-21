package sa.urtv.home;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
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
 *
 * Two looks. On the favourite: the posters in a row under the header. Up on a poster («the lens»): that poster
 * grows by half to the middle of the screen and everything else goes soft behind it, as through a lens wide
 * open; Left brings the next one to the middle and this one slides to the right. Android 11 has no live blur,
 * so the soft parts are pictures blurred once: the screen as it was, and a small copy of each poster.
 */
final class Showcase {
    static final class Item {
        String pkg, title, detail, group, poster, wide, intent;
        float aspect = 16 / 9f;
        int progress = -1;   // percent watched («watch next»), or of the show on now (live)
        boolean live;
    }

    // ---------------------------------------------------------------- what the user chose to see (TvSettings)

    static final String OFF = "showcase_off", LIVE_OFF = "shahid_live_off", LIVE_HIDDEN = "shahid_live_hidden";

    static java.util.Set<String> set(Context c, String key) {
        String v = Dock.prefs(c).getString(key, "");
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String x : v.split(",")) if (!x.isEmpty()) out.add(x);
        return out;
    }

    static void putSet(Context c, String key, java.util.Set<String> v) {
        Dock.prefs(c).edit().putString(key, android.text.TextUtils.join(",", v)).apply();
    }

    static boolean liveOn(Context c) { return !Dock.prefs(c).getBoolean(LIVE_OFF, false); }

    /** live channels first (Shahid), then «watch next» and the app's rows; none for an app switched off */
    private List<Item> itemsFor(String pkg) {
        List<Item> l = new ArrayList<>();
        if (set(m, OFF).contains(pkg)) return l;
        if (ShahidLive.PKG.equals(pkg) && liveOn(m)) l.addAll(ShahidLive.items(set(m, LIVE_HIDDEN)));
        List<Item> d = data.get(pkg);
        if (d != null) l.addAll(d);
        return l;
    }

    private static final Interpolator EASE = new PathInterpolator(0.32f, 0.72f, 0f, 1f);
    private static final int MAX_ITEMS = 12;
    private static final long FRESH_MS = 2 * 60 * 1000;
    private static final long MOVE_MS = 560;
    private static final float LENS = 1.5f;

    private final MainActivity m;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> images = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private final FrameLayout hero, backdrop;
    private final ImageView[] backs = new ImageView[2];
    private int backFront = 0;
    private String backUrl;

    // row labels (over the row) and the lens caption (under the poster in the middle)
    private final LinearLayout labels, caption;
    private final TextView group, detail, capGroup, capTitle;
    // the soft screen behind the lens, and the stage the posters move on
    private final FrameLayout glass, stage;
    private final ImageView glassShot, glassTint;

    private volatile Map<String, List<Item>> data = new HashMap<>();
    private long loadedAt;
    private String shownPkg;
    private List<Item> items = new ArrayList<>();
    private final List<Poster> posters = new ArrayList<>();
    private int lensAt = -1;   // the poster in the middle, -1 when the posters are a row
    private final Runnable backdropLater = this::backdropFromFocus;

    /** one poster: the sharp picture, a soft copy for when it is beside the lens, and a dimmer */
    private final class Poster {
        final FrameLayout view;
        final ImageView sharp, soft;
        final View dim, ring;
        final Item item;
        final int w, h;

        Poster(Item it, int h) {
            item = it;
            this.h = h;
            w = Math.round(h * it.aspect);
            view = new FrameLayout(m);
            view.setId(View.generateViewId());
            view.setFocusable(true);
            view.setClickable(true);
            view.setClipToOutline(true);
            view.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), m.dp(8)); }
            });
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF22252B);
            view.setBackground(bg);
            TextView name = m.text(12, "medium", 0xFFFFFDF6);
            name.setText(it.title);
            name.setGravity(Gravity.CENTER);
            name.setPadding(m.dp(8), 0, m.dp(8), 0);
            view.addView(name, new FrameLayout.LayoutParams(-1, -1));   // until the picture arrives, or if it doesn't
            sharp = new ImageView(m);
            sharp.setScaleType(ImageView.ScaleType.CENTER_CROP);
            sharp.setAlpha(0f);
            view.addView(sharp, new FrameLayout.LayoutParams(-1, -1));
            soft = new ImageView(m);
            soft.setScaleType(ImageView.ScaleType.CENTER_CROP);
            soft.setAlpha(0f);
            view.addView(soft, new FrameLayout.LayoutParams(-1, -1));
            dim = new View(m);
            dim.setBackgroundColor(0xFF000000);
            dim.setAlpha(0f);
            view.addView(dim, new FrameLayout.LayoutParams(-1, -1));
            if (it.progress >= 0) {
                View track = new View(m);
                track.setBackgroundColor(0x66000000);
                view.addView(track, new FrameLayout.LayoutParams(-1, m.dp(4), Gravity.BOTTOM));
                View done = new View(m);
                done.setBackgroundColor(MainActivity.GOLD);
                view.addView(done, new FrameLayout.LayoutParams(w * it.progress / 100, m.dp(4), Gravity.BOTTOM | Gravity.RIGHT));
            }
            if (it.live) {
                TextView badge = m.text(11, "medium", 0xFFFFFFFF);
                badge.setText("مباشر");
                badge.setPadding(m.dp(7), m.dp(1), m.dp(7), m.dp(2));
                GradientDrawable bb = new GradientDrawable();
                bb.setCornerRadius(m.dp(4));
                bb.setColor(0xE6D93A3A);
                badge.setBackground(bb);
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
                bl.setMargins(0, m.dp(6), m.dp(6), 0);
                view.addView(badge, bl);
            }
            GradientDrawable rd = new GradientDrawable();
            rd.setCornerRadius(m.dp(8));
            rd.setStroke(m.dp(2), 0xE6FFFFFF);
            ring = new View(m);
            ring.setBackground(rd);
            ring.setAlpha(0f);
            view.addView(ring, new FrameLayout.LayoutParams(-1, -1));
            view.setPivotX(w / 2f);
            view.setPivotY(h / 2f);
            view.setOnClickListener(v -> m.openItem(it));
            if (it.poster != null) fetch(it.poster, it.wide, w, h, bm -> {
                sharp.setImageBitmap(bm);
                sharp.animate().alpha(soft.getAlpha() > 0 ? 0f : 1f).setDuration(260).start();
                pool.execute(() -> {
                    Bitmap b = blur(Bitmap.createScaledBitmap(bm, Math.max(8, bm.getWidth() / 8), Math.max(8, bm.getHeight() / 8), true), 3);
                    main.post(() -> soft.setImageBitmap(b));
                });
            });
        }

        /** sharp and bright in the lens or in the row; soft and dimmed beside the lens */
        void look(boolean softly, boolean animate) {
            long d = animate ? MOVE_MS : 0;
            boolean pic = sharp.getDrawable() != null;
            sharp.animate().alpha(pic && !softly ? 1f : 0f).setDuration(d).setInterpolator(EASE).start();
            soft.animate().alpha(softly && soft.getDrawable() != null ? 1f : 0f).setDuration(d).setInterpolator(EASE).start();
            dim.animate().alpha(softly ? 0.5f : 0f).setDuration(d).setInterpolator(EASE).start();
        }
    }

    Showcase(MainActivity m, FrameLayout hero, FrameLayout backdrop) {
        this.m = m;
        this.hero = hero;
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

        labels = new LinearLayout(m);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.RIGHT);
        labels.setAlpha(0f);
        labels.setVisibility(View.GONE);
        group = m.text(13, "medium", MainActivity.GOLD);
        group.setGravity(Gravity.RIGHT);
        group.setSingleLine(true);
        labels.addView(group, new LinearLayout.LayoutParams(-1, -2));
        detail = m.text(18, "medium", 0xFFFFFDF6);
        detail.setGravity(Gravity.RIGHT);
        detail.setSingleLine(true);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detail.setShadowLayer(m.dp(3), 0, m.dp(1), 0x80000000);
        labels.addView(detail, new LinearLayout.LayoutParams(-1, -2));

        glass = new FrameLayout(m);
        glass.setAlpha(0f);
        glass.setVisibility(View.GONE);
        glassShot = new ImageView(m);
        glassShot.setScaleType(ImageView.ScaleType.FIT_XY);
        glass.addView(glassShot, new FrameLayout.LayoutParams(-1, -1));
        glassTint = new ImageView(m);   // the poster in the middle, very soft, over the screen: its colour leads
        glassTint.setScaleType(ImageView.ScaleType.CENTER_CROP);
        glassTint.setAlpha(0f);
        glass.addView(glassTint, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(m);
        shade.setBackgroundColor(0x66000000);
        glass.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        stage = new FrameLayout(m);
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        stage.setVisibility(View.GONE);

        caption = new LinearLayout(m);
        caption.setOrientation(LinearLayout.VERTICAL);
        caption.setGravity(Gravity.CENTER_HORIZONTAL);
        caption.setAlpha(0f);
        capGroup = m.text(14, "medium", MainActivity.GOLD);
        capGroup.setGravity(Gravity.CENTER);
        caption.addView(capGroup, new LinearLayout.LayoutParams(-1, -2));
        capTitle = m.text(22, "medium", 0xFFFFFDF6);
        capTitle.setGravity(Gravity.CENTER);
        capTitle.setMaxLines(2);
        capTitle.setLineSpacing(0, 1.1f);
        capTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        capTitle.setShadowLayer(m.dp(4), 0, m.dp(1), 0x99000000);
        caption.addView(capTitle, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Over everything else on the home screen, so the soft screen covers the clock, the bar and the favourites. */
    void attach() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.RIGHT | Gravity.TOP);
        lp.setMargins(m.dp(48), m.dp(104), m.dp(48), 0);
        hero.addView(labels, lp);
        hero.addView(glass, new FrameLayout.LayoutParams(-1, -1));
        hero.addView(stage, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2);
        cp.leftMargin = cp.rightMargin = m.dp(170);   // under the poster, not from edge to edge
        stage.addView(caption, cp);
    }

    // ---------------------------------------------------------------- data

    /** Reads the database again (in the background) if it is older than two minutes, or when forced. */
    void refresh(boolean force) {
        if (!force && System.currentTimeMillis() - loadedAt < FRESH_MS) return;
        loadedAt = System.currentTimeMillis();
        ContentResolver cr = m.getContentResolver();
        boolean live = liveOn(m) && m.hasPackage(ShahidLive.PKG);
        pool.execute(() -> {
            if (live) ShahidLive.refresh();
            Map<String, List<Item>> d = load(cr);
            main.post(() -> {
                data = d;
                m.showcaseReady();
            });
        });
    }

    boolean has(String pkg) { return !itemsFor(pkg).isEmpty(); }

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
                    if (left > 0) it.detail = it.detail + " — بقي " + ShahidLive.minutes(left);
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

    boolean inLens() { return lensAt >= 0; }

    boolean owns(View v) {
        for (Poster p : posters) if (p.view == v) return true;
        return false;
    }

    int firstPosterId() { return posters.isEmpty() ? View.NO_ID : posters.get(0).view.getId(); }

    /** The app's posters in a row under the header; false (and hidden) when it publishes nothing. */
    boolean show(String pkg) {
        if (!has(pkg)) { hide(true); return false; }
        if (lensAt >= 0) lens(-1);
        if (pkg.equals(shownPkg)) return true;
        boolean was = shownPkg != null;
        shownPkg = pkg;
        build(itemsFor(pkg));
        labels.setVisibility(View.VISIBLE);
        stage.setVisibility(View.VISIBLE);
        for (View v : new View[]{labels, stage}) {
            v.animate().cancel();
            v.setAlpha(0f);
            v.setTranslationY(m.dp(was ? 10 : 16));
            v.animate().alpha(1f).translationY(0).setStartDelay(was ? 40 : 120).setDuration(460).setInterpolator(EASE).start();
        }
        backdrop.animate().cancel();
        backdrop.animate().alpha(1f).setStartDelay(0).setDuration(600).setInterpolator(EASE).start();
        setBackdrop(items.get(0).wide);
        return true;
    }

    void hide(boolean animate) {
        if (shownPkg == null && stage.getVisibility() == View.GONE) return;
        if (lensAt >= 0) lens(-1);
        shownPkg = null;
        main.removeCallbacks(backdropLater);
        for (View v : new View[]{labels, stage, backdrop}) v.animate().cancel();
        if (!animate) {
            labels.setAlpha(0f);
            labels.setVisibility(View.GONE);
            stage.setVisibility(View.GONE);
            backdrop.setAlpha(0f);
            return;
        }
        labels.animate().alpha(0f).translationY(m.dp(10)).setStartDelay(0).setDuration(260).setInterpolator(EASE)
                .withEndAction(() -> { if (shownPkg == null) labels.setVisibility(View.GONE); }).start();
        stage.animate().alpha(0f).translationY(m.dp(10)).setStartDelay(0).setDuration(260).setInterpolator(EASE)
                .withEndAction(() -> { if (shownPkg == null) stage.setVisibility(View.GONE); }).start();
        backdrop.animate().alpha(0f).setStartDelay(0).setDuration(420).setInterpolator(EASE).start();
    }

    private void build(List<Item> list) {
        items = list;
        for (Poster p : posters) stage.removeView(p.view);
        posters.clear();
        lensAt = -1;
        int h = m.dp(118);
        for (Item it : list) {
            Poster p = new Poster(it, h);
            stage.addView(p.view, stage.getChildCount() - 1, new FrameLayout.LayoutParams(p.w, p.h, Gravity.LEFT | Gravity.TOP));
            p.view.setOnFocusChangeListener((v, has) -> { if (has) focused(p); });
            posters.add(p);
        }
        // the first item at the right end (the app doesn't declare supportsRtl, so Arabic order is by hand)
        for (int i = 0; i < posters.size(); i++) {
            View v = posters.get(i).view;
            v.setNextFocusRightId(i == 0 ? v.getId() : posters.get(i - 1).view.getId());
            v.setNextFocusLeftId(i == posters.size() - 1 ? v.getId() : posters.get(i + 1).view.getId());
        }
        group.setText(list.get(0).group);
        detail.setText(list.get(0).detail);
        place(false);
    }

    /** Up from a poster goes to the top bar, Down back to the favourite they belong to. */
    void wire(int up, int down) {
        for (Poster p : posters) { p.view.setNextFocusUpId(up); p.view.setNextFocusDownId(down); }
    }

    private void focused(Poster p) {
        int i = posters.indexOf(p);
        if (i != lensAt) lens(i);
        main.removeCallbacks(backdropLater);
        main.postDelayed(backdropLater, 280);   // passing quickly over posters doesn't flash the backdrop
    }

    /** The lens on poster i (-1: back to the row). Entering it, the screen as it is goes soft behind. */
    void lens(int i) {
        boolean entering = lensAt < 0 && i >= 0, leaving = lensAt >= 0 && i < 0;
        lensAt = i;
        if (entering) {
            glassShot.setImageBitmap(snapshot());
            glass.setVisibility(View.VISIBLE);
            glass.animate().cancel();
            glass.animate().alpha(1f).setDuration(420).setInterpolator(EASE).start();
            labels.animate().alpha(0f).setDuration(200).start();
        } else if (leaving) {
            glass.animate().cancel();
            glass.animate().alpha(0f).setDuration(360).setInterpolator(EASE)
                    .withEndAction(() -> { if (lensAt < 0) glass.setVisibility(View.GONE); }).start();
            labels.animate().alpha(1f).setDuration(300).start();
        }
        if (i >= 0) {
            Item it = posters.get(i).item;
            capGroup.setText(it.group);
            capTitle.setText(it.detail);
            group.setText(it.group);
            detail.setText(it.detail);
            android.graphics.drawable.Drawable sd = posters.get(i).soft.getDrawable();
            Bitmap soft = sd instanceof android.graphics.drawable.BitmapDrawable ? ((android.graphics.drawable.BitmapDrawable) sd).getBitmap() : null;
            if (soft != null) {
                glassTint.setImageBitmap(soft);
                glassTint.animate().cancel();
                glassTint.setAlpha(0f);
                glassTint.animate().alpha(0.55f).setDuration(MOVE_MS).setInterpolator(EASE).start();
            }
        }
        place(true);
    }

    /** Every poster to its place: a row under the header, or around the one in the middle of the screen. */
    private void place(boolean animate) {
        int sw = m.getResources().getDisplayMetrics().widthPixels, sh = m.getResources().getDisplayMetrics().heightPixels;
        long d = animate ? MOVE_MS : 0;
        if (lensAt < 0) {
            int x = sw - m.dp(48), y = m.dp(168), gap = m.dp(24);
            for (Poster p : posters) {
                x -= p.w;
                move(p.view, x, y, 1f, d);
                p.look(false, animate);
                p.ring.animate().alpha(0f).setDuration(160).start();
                p.view.setTranslationZ(0);
                x -= gap;
            }
            caption.animate().alpha(0f).setDuration(200).start();
            return;
        }
        float cx = sw / 2f, cy = sh * 0.44f, gap = m.dp(44);
        Poster f = posters.get(lensAt);
        move(f.view, cx - f.w / 2f, cy - f.h / 2f, LENS, d);
        f.look(false, animate);
        f.ring.animate().alpha(1f).setDuration(200).start();
        f.view.setTranslationZ(m.dp(16));
        // to the right: the ones before it (Arabic order); to the left: the ones after it
        float edge = cx + f.w * LENS / 2f;
        for (int j = lensAt - 1; j >= 0; j--) {
            Poster p = posters.get(j);
            edge += gap;
            move(p.view, edge, cy - p.h / 2f, 1f, d);
            edge += p.w;
            beside(p, animate);
        }
        edge = cx - f.w * LENS / 2f;
        for (int j = lensAt + 1; j < posters.size(); j++) {
            Poster p = posters.get(j);
            edge -= gap + p.w;
            move(p.view, edge, cy - p.h / 2f, 1f, d);
            beside(p, animate);
        }
        FrameLayout.LayoutParams cl = (FrameLayout.LayoutParams) caption.getLayoutParams();
        int top = Math.round(cy + f.h * LENS / 2f + m.dp(20));
        if (cl.topMargin != top) { cl.topMargin = top; caption.setLayoutParams(cl); }
        caption.animate().alpha(1f).setDuration(300).setStartDelay(120).start();
    }

    private void beside(Poster p, boolean animate) {
        p.look(true, animate);
        p.ring.animate().alpha(0f).setDuration(160).start();
        p.view.setTranslationZ(0);
    }

    private void move(View v, float x, float y, float s, long d) {
        v.animate().cancel();
        if (d == 0) { v.setTranslationX(x); v.setTranslationY(y); v.setScaleX(s); v.setScaleY(s); return; }
        v.animate().translationX(x).translationY(y).scaleX(s).scaleY(s).setDuration(d).setInterpolator(EASE).start();
    }

    /** The screen as it is now (without the posters), small and blurred: the glass behind the lens. */
    private Bitmap snapshot() {
        View root = m.getWindow().getDecorView();
        int f = 6, w = Math.max(1, root.getWidth() / f), h = Math.max(1, root.getHeight() / f);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(1f / f, 1f / f);
        // left out by transparency, not visibility: hiding the stage would take the focus off the poster
        float sa = stage.getAlpha(), ga = glass.getAlpha();
        stage.setAlpha(0f);
        glass.setAlpha(0f);
        root.draw(c);
        stage.setAlpha(sa);
        glass.setAlpha(ga);
        return blur(b, 4);
    }

    /** A box blur, three passes each way: near enough to a lens blur on a small picture. */
    static Bitmap blur(Bitmap src, int r) {
        Bitmap b = src.isMutable() && src.getConfig() == Bitmap.Config.ARGB_8888 ? src : src.copy(Bitmap.Config.ARGB_8888, true);
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h], tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            boxH(px, tmp, w, h, r);
            boxH(transpose(tmp, w, h, px), tmp, h, w, r);
            transpose(tmp, h, w, px);
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }

    private static int[] transpose(int[] in, int w, int h, int[] out) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out[x * h + y] = in[y * w + x];
        return out;
    }

    private static void boxH(int[] in, int[] out, int w, int h, int r) {
        int div = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int a = 0, rr = 0, g = 0, bb = 0, row = y * w;
            for (int k = -r; k <= r; k++) {
                int p = in[row + Math.min(w - 1, Math.max(0, k))];
                a += p >>> 24; rr += (p >> 16) & 255; g += (p >> 8) & 255; bb += p & 255;
            }
            for (int x = 0; x < w; x++) {
                out[row + x] = ((a / div) << 24) | ((rr / div) << 16) | ((g / div) << 8) | (bb / div);
                int pOut = in[row + Math.max(0, x - r)], pIn = in[row + Math.min(w - 1, x + r + 1)];
                a += (pIn >>> 24) - (pOut >>> 24);
                rr += ((pIn >> 16) & 255) - ((pOut >> 16) & 255);
                g += ((pIn >> 8) & 255) - ((pOut >> 8) & 255);
                bb += (pIn & 255) - (pOut & 255);
            }
        }
    }

    private void backdropFromFocus() {
        View f = m.getCurrentFocus();
        for (Poster p : posters) if (p.view == f) setBackdrop(p.item.wide);
    }

    private void setBackdrop(String url) {
        if (url == null || url.equals(backUrl)) return;
        backUrl = url;
        int w = m.getResources().getDisplayMetrics().widthPixels / 2;   // shown dimmed and big: half size is plenty
        fetch(url, null, w, w * 9 / 16, bm -> {
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

    /** a picture, or (when it doesn't load) the fallback: a live show without its poster takes the channel's */
    private void fetch(String url, String fallback, int w, int h, Got got) {
        String key = url + "@" + w;
        Bitmap cached = images.get(key);
        if (cached != null) { got.got(cached); return; }
        Context c = m.getApplicationContext();
        pool.execute(() -> {
            Bitmap bm = decode(c, url, w, h);
            if (bm == null && fallback != null && !fallback.equals(url)) bm = decode(c, fallback, w, h);
            if (bm == null) return;
            images.put(key, bm);
            Bitmap done = bm;
            main.post(() -> got.got(done));
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
            // sharp enough for the lens (half as big again as in the row)
            while (o.outWidth / (s * 2) >= w * LENS && o.outHeight / (s * 2) >= h * LENS) s *= 2;
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

    void destroy() { pool.shutdownNow(); }

    static Intent parse(Item it) {
        if (it.intent == null) return null;
        try { return Intent.parseUri(it.intent, Intent.URI_INTENT_SCHEME); }
        catch (Exception e) { return null; }
    }
}
