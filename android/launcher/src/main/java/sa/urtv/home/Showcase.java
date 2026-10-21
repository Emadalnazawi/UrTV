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
        boolean live;          // a live channel with its guide: the show on now
        String channelId;      // Shahid live: the channel (its card can play the stream)
        boolean vip;           // plays only in Shahid, with the subscription
        String start, end, left, next;   // live: the show's times, «باقي ٧ د», «التالي»
        ThmanyahMatches.Match match;     // ثمانية: a league match, drawn as its own card
        boolean startHere;               // the lens starts on this one (the match nearest to now)
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
        if (ThmanyahMatches.PKG.equals(pkg)) l.addAll(ThmanyahMatches.items());
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
    private final TextView group, detail, capGroup, capTitle, tStart, tLeft, tEnd, tNext;
    private final LinearLayout timeBox, nextRow;
    private final View barFill;
    // the soft screen behind the lens, and the stage the posters move on
    private final FrameLayout glass, stage;
    private final ImageView glassShot, glassTint;

    private volatile Map<String, List<Item>> data = new HashMap<>();
    private long loadedAt;
    private String shownPkg;
    private List<Item> items = new ArrayList<>();
    private final List<Poster> posters = new ArrayList<>();
    private int lensAt = -1;   // the poster in the middle, -1 when the posters are a row
    // ثمانية's matches don't use the lens: they stay in their row, which slides to the focused one
    private int rowAt = -1;
    private float rowShift;
    private boolean rowToStart;
    private final Runnable backdropLater = this::backdropFromFocus;

    /** one poster: the sharp picture, a soft copy for when it is beside the lens, and a dimmer */
    private final class Poster {
        final FrameLayout view;
        final ImageView sharp, soft;
        final View dim, ring;
        View barTrack, barDone;   // the card's own progress line (hidden in the lens: the bar under it says it)
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
            if (it.match == null) view.addView(name, new FrameLayout.LayoutParams(-1, -1));   // until the picture arrives, or if it doesn't
            else matchCard(it.match);
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
                barTrack = new View(m);
                barTrack.setBackgroundColor(0x66000000);
                view.addView(barTrack, new FrameLayout.LayoutParams(-1, m.dp(4), Gravity.BOTTOM));
                barDone = new View(m);
                barDone.setBackgroundColor(MainActivity.GOLD);
                view.addView(barDone, new FrameLayout.LayoutParams(w * it.progress / 100, m.dp(4), Gravity.BOTTOM | Gravity.RIGHT));
            }
            if (it.live) {
                // the one sign that this is live: a red dot in the corner (no word, nothing by the name)
                View dot = new View(m);
                GradientDrawable dd = new GradientDrawable();
                dd.setShape(GradientDrawable.OVAL);
                dd.setColor(0xFFE5484D);
                dd.setStroke(m.dp(2), 0x99000000);
                dot.setBackground(dd);
                // the left corner: channels put their own logo at the right
                FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(m.dp(12), m.dp(12), Gravity.TOP | Gravity.LEFT);
                dl.setMargins(m.dp(8), m.dp(8), 0, 0);
                view.addView(dot, dl);
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
                    Bitmap b = blur(Bitmap.createScaledBitmap(bm, Math.max(8, bm.getWidth() / 6), Math.max(8, bm.getHeight() / 6), true), 2);
                    main.post(() -> soft.setImageBitmap(b));
                });
            });
        }

        /**
         * A match: each team on its own side, in its colour (taken from its crest), its crest large and soft
         * behind its name, the two colours meeting in the middle; there the result or the kick-off and day,
         * the ground at the bottom. One sign per state: a result alone is a finished game, a red dot by it a
         * game on now, «مؤجلة» in its place a postponed one.
         */
        private void matchCard(ThmanyahMatches.Match mt) {
            FrameLayout card = new FrameLayout(m);
            ImageView cloth = new ImageView(m);
            cloth.setScaleType(ImageView.ScaleType.FIT_XY);
            card.addView(cloth, new FrameLayout.LayoutParams(-1, -1));
            int[] colours = {0xFF2A2A2A, 0xFF2A2A2A};   // away (left), home (right)
            Bitmap[] crestPics = new Bitmap[2];
            cloth.setImageBitmap(fabric(w, h, colours[0], colours[1], null, null));
            TextView home = m.text(13, "medium", 0xFFFFFFFF), away = m.text(13, "medium", 0xFFFFFFFF);
            for (TextView t : new TextView[]{home, away}) { t.setShadowLayer(m.dp(4), 0, m.dp(1), 0x99000000); t.setSingleLine(true); }
            home.setText(mt.home);
            away.setText(mt.away);
            FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
            hl.setMargins(0, m.dp(12), m.dp(12), 0);
            card.addView(home, hl);
            FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
            al.setMargins(m.dp(12), m.dp(12), 0, 0);
            card.addView(away, al);

            LinearLayout mid = new LinearLayout(m);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout scoreRow = new LinearLayout(m);
            scoreRow.setOrientation(LinearLayout.HORIZONTAL);
            scoreRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView big = m.text(mt.homeScore >= 0 ? 26 : 17, mt.homeScore >= 0 ? "display" : "medium", 0xFFFFFFFF);
            big.setShadowLayer(m.dp(6), 0, m.dp(1), 0x99000000);
            if (mt.postponed) big.setText("مؤجلة");
            // the away score at the left, the home score at the right, as the teams sit
            else if (mt.homeScore >= 0) big.setText(MainActivity.arabicDigits(mt.awayScore + " - " + mt.homeScore));
            else big.setText(mt.time);
            if (mt.live) {
                View dot = new View(m);
                GradientDrawable dd = new GradientDrawable();
                dd.setShape(GradientDrawable.OVAL);
                dd.setColor(0xFFE5484D);
                dot.setBackground(dd);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(m.dp(8), m.dp(8));
                dl.rightMargin = m.dp(6);
                scoreRow.addView(dot, dl);
            }
            scoreRow.addView(big, new LinearLayout.LayoutParams(-2, -2));
            mid.addView(scoreRow, new LinearLayout.LayoutParams(-2, -2));
            // the day where it tells something: a coming game, or a result from another day than today
            boolean today = android.text.format.DateUtils.isToday(mt.at);
            if (!mt.postponed && !mt.live && (mt.homeScore < 0 || !today)) {
                TextView day = m.text(10, "regular", 0xD9FFFFFF);
                day.setText(mt.day);
                day.setShadowLayer(m.dp(3), 0, 0, 0x99000000);
                mid.addView(day, new LinearLayout.LayoutParams(-2, -2));
            }
            card.addView(mid, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            if (mt.venue != null) {
                TextView venue = m.text(8, "regular", 0xCCFFFFFF);
                venue.setText(mt.venue);
                venue.setSingleLine(true);
                venue.setShadowLayer(m.dp(3), 0, 0, 0x99000000);
                FrameLayout.LayoutParams vl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                vl.bottomMargin = m.dp(8);
                card.addView(venue, vl);
            }
            view.addView(card, new FrameLayout.LayoutParams(-1, -1));
            String[] urls = {mt.awayBadge, mt.homeBadge};
            int[] left = {2};
            Runnable weave = () -> pool.execute(() -> {
                Bitmap b = fabric(w, h, colours[0], colours[1], crestPics[0], crestPics[1]);
                main.post(() -> cloth.setImageBitmap(b));
            });
            for (int k = 0; k < 2; k++) {
                final int side = k;
                if (urls[k] == null) { if (--left[0] == 0) weave.run(); continue; }
                fetch(urls[k], null, h * 2, h * 2, bm -> {
                    colours[side] = teamColour(bm);
                    crestPics[side] = bm;
                    if (--left[0] == 0) weave.run();
                });
            }
        }

        /** sharp and bright in the lens or in the row; soft and dimmed beside the lens */
        void look(boolean softly, boolean animate) {
            long d = animate ? MOVE_MS : 0;
            boolean pic = sharp.getDrawable() != null || item.match != null;
            if (item.match == null) sharp.animate().alpha(pic && !softly ? 1f : 0f).setDuration(d).setInterpolator(EASE).start();
            soft.animate().alpha(softly && soft.getDrawable() != null ? 1f : 0f).setDuration(d).setInterpolator(EASE).start();
            dim.animate().alpha(softly ? 0.4f : 0f).setDuration(d).setInterpolator(EASE).start();
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
        caption.setGravity(Gravity.RIGHT);
        caption.setAlpha(0f);
        capGroup = m.text(14, "medium", MainActivity.GOLD);
        capGroup.setGravity(Gravity.RIGHT);
        caption.addView(capGroup, new LinearLayout.LayoutParams(-1, -2));
        capTitle = m.text(22, "medium", 0xFFFFFDF6);
        capTitle.setGravity(Gravity.RIGHT);
        capTitle.setMaxLines(2);
        capTitle.setLineSpacing(0, 1.1f);
        capTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        capTitle.setShadowLayer(m.dp(4), 0, m.dp(1), 0x99000000);
        caption.addView(capTitle, new LinearLayout.LayoutParams(-1, -2));

        // a live show, always in the same order: its time bar (start at the right, end at the left, what is
        // left in the middle), then «التالي»
        timeBox = new LinearLayout(m);
        timeBox.setOrientation(LinearLayout.VERTICAL);
        FrameLayout bar = new FrameLayout(m);
        View track = new View(m);
        GradientDrawable tg = new GradientDrawable();
        tg.setCornerRadius(m.dp(2));
        tg.setColor(0x2EFFFFFF);
        track.setBackground(tg);
        bar.addView(track, new FrameLayout.LayoutParams(-1, -1));
        barFill = new View(m);
        GradientDrawable fg = new GradientDrawable();
        fg.setCornerRadius(m.dp(2));
        fg.setColor(MainActivity.GOLD);
        barFill.setBackground(fg);
        bar.addView(barFill, new FrameLayout.LayoutParams(0, -1, Gravity.RIGHT));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, m.dp(4));
        bl.topMargin = m.dp(10);
        timeBox.addView(bar, bl);
        LinearLayout times = new LinearLayout(m);
        times.setOrientation(LinearLayout.HORIZONTAL);
        tEnd = m.text(13, "regular", 0xFFB9B2A3);
        tLeft = m.text(13, "medium", 0xFFF6EEDC);
        tLeft.setGravity(Gravity.CENTER);
        tStart = m.text(13, "regular", 0xFFB9B2A3);
        times.addView(tEnd, new LinearLayout.LayoutParams(-2, -2));          // laid out left to right by hand
        times.addView(tLeft, new LinearLayout.LayoutParams(0, -2, 1f));
        times.addView(tStart, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = m.dp(4);
        timeBox.addView(times, tl);
        caption.addView(timeBox, new LinearLayout.LayoutParams(-1, -2));
        nextRow = new LinearLayout(m);
        nextRow.setOrientation(LinearLayout.HORIZONTAL);
        nextRow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        tNext = m.text(14, "regular", 0xFFD8D1C2);
        tNext.setSingleLine(true);
        tNext.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nextRow.addView(tNext, new LinearLayout.LayoutParams(0, -2, 1f));
        tNext.setGravity(Gravity.RIGHT);
        TextView tag = m.text(11, "medium", MainActivity.GOLD);
        tag.setText("التالي");
        tag.setPadding(m.dp(7), m.dp(1), m.dp(7), m.dp(2));
        GradientDrawable tb = new GradientDrawable();
        tb.setCornerRadius(m.dp(4));
        tb.setColor(0x1FFFFFFF);
        tag.setBackground(tb);
        LinearLayout.LayoutParams tgl = new LinearLayout.LayoutParams(-2, -2);
        tgl.leftMargin = m.dp(8);
        nextRow.addView(tag, tgl);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = m.dp(10);
        caption.addView(nextRow, nl);
    }

    /** Over everything else on the home screen, so the soft screen covers the clock, the bar and the favourites. */
    void attach() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.RIGHT | Gravity.TOP);
        lp.setMargins(m.dp(48), m.dp(104), m.dp(48), 0);
        hero.addView(labels, lp);
        hero.addView(glass, new FrameLayout.LayoutParams(-1, -1));
        hero.addView(stage, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(caption, new FrameLayout.LayoutParams(m.dp(300), -2, Gravity.LEFT | Gravity.TOP));   // as wide as the poster in the lens (place)
    }

    // ---------------------------------------------------------------- data

    /** Reads the database again (in the background) if it is older than two minutes, or when forced. */
    void refresh(boolean force) {
        if (!force && System.currentTimeMillis() - loadedAt < FRESH_MS) return;
        loadedAt = System.currentTimeMillis();
        ContentResolver cr = m.getContentResolver();
        boolean live = liveOn(m) && m.hasPackage(ShahidLive.PKG), league = m.hasPackage(ThmanyahMatches.PKG);
        Context app = m.getApplicationContext();
        pool.execute(() -> {
            if (live) ShahidLive.refresh();
            if (league) ThmanyahMatches.refresh(app);
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

    int firstPosterId() {
        if (posters.isEmpty()) return View.NO_ID;
        for (Poster p : posters) if (p.item.startHere) return p.view.getId();
        return posters.get(0).view.getId();
    }

    /** The app's posters in a row under the header; false (and hidden) when it publishes nothing. */
    boolean show(String pkg) {
        if (!has(pkg)) { hide(true); return false; }
        if (lensAt >= 0) lens(-1);
        if (rowAt >= 0) { rowAt = -1; place(true); }
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
        return true;   // the wallpaper stays as it is: no picture of the content behind the posters
    }

    void hide(boolean animate) {
        stopPreview();
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
        rowAt = -1;
        rowShift = 0;
        rowToStart = true;
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
        if (p.item.match != null) { rowAt = i; place(true); return; }
        if (i != lensAt) lens(i);
        stopPreview();
        if (p.item.channelId != null) main.postDelayed(previewLater, 900);   // only where the focus stays
    }

    /** The lens on poster i (-1: back to the row). Entering it, the screen as it is goes soft behind. */
    void lens(int i) {
        if (i < 0) stopPreview();
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
            capTitle.setText(it.live ? it.title : it.detail);
            capTitle.setVisibility(it.match != null ? View.GONE : View.VISIBLE);   // the card says it all
            timeBox.setVisibility(it.live ? View.VISIBLE : View.GONE);
            nextRow.setVisibility(it.live && it.next != null ? View.VISIBLE : View.GONE);
            if (it.live) {
                tStart.setText(it.start);
                tEnd.setText(it.end);
                tLeft.setText(it.left);
                tNext.setText(it.next);
            }
            group.setText(it.group);
            detail.setText(it.detail);
            detail.setVisibility(it.detail == null || it.detail.isEmpty() ? View.GONE : View.VISIBLE);
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
            int y = m.dp(168), gap = m.dp(24), edgeR = sw - m.dp(48), edgeL = m.dp(48);
            int[] xs = new int[posters.size()];
            int x = edgeR;
            for (int j = 0; j < posters.size(); j++) { x -= posters.get(j).w; xs[j] = x; x -= gap; }
            // the row starts on the item marked to start (today's matches) at its right end; the focused one
            // stays inside the screen, the row sliding as little as it can
            if (rowToStart) {
                rowToStart = false;
                for (int j = 0; j < posters.size(); j++)
                    if (posters.get(j).item.startHere) rowShift = edgeR - (xs[j] + posters.get(j).w);
            }
            if (rowAt >= 0) {
                Poster f = posters.get(rowAt);
                float l = xs[rowAt] + rowShift, r = l + f.w;
                if (r > edgeR) rowShift -= r - edgeR;
                if (l < edgeL) rowShift += edgeL - l;
            }
            for (int j = 0; j < posters.size(); j++) {
                Poster p = posters.get(j);
                boolean on = j == rowAt;
                move(p.view, xs[j] + rowShift, y, on ? 1.06f : 1f, d);
                p.look(false, animate);
                p.ring.animate().alpha(on ? 1f : 0f).setDuration(160).start();
                p.view.setTranslationZ(on ? m.dp(10) : 0);
            }
            caption.animate().alpha(0f).setDuration(200).start();
            for (Poster p : posters) if (p.barTrack != null) { p.barTrack.animate().alpha(1f).start(); p.barDone.animate().alpha(1f).start(); }
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
        int top = Math.round(cy + f.h * LENS / 2f + m.dp(20)), cw = Math.round(f.w * LENS), left = Math.round(cx - cw / 2f);
        if (cl.topMargin != top || cl.width != cw || cl.leftMargin != left) {
            cl.topMargin = top; cl.width = cw; cl.leftMargin = left;
            caption.setLayoutParams(cl);
        }
        if (f.item.live) {
            FrameLayout.LayoutParams fl = (FrameLayout.LayoutParams) barFill.getLayoutParams();
            fl.width = cw * Math.max(0, f.item.progress) / 100;
            barFill.setLayoutParams(fl);
        }
        for (Poster p : posters) {
            float a = p == f ? 0f : 1f;
            if (p.barTrack != null) { p.barTrack.animate().alpha(a).setDuration(200).start(); p.barDone.animate().alpha(a).setDuration(200).start(); }
        }
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
        return blur(b, 2);   // a little soft, not lost
    }

    /**
     * The card's cloth: the two team colours meeting in the middle, a fine weave of threads over them, each
     * crest pressed into the cloth on its own side (tone on tone, as on a jersey), and a soft shade where the
     * text sits (names at the top, the result in the middle, the ground at the bottom) so it always reads.
     */
    static Bitmap fabric(int w, int h, int away, int home, Bitmap awayCrest, Bitmap homeCrest) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        p.setShader(new android.graphics.LinearGradient(0, 0, w, 0, new int[]{away, away, home, home},
                new float[]{0f, 0.38f, 0.62f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        // the crests, pressed in: a dark edge below, a light one above, the crest itself faint and colourless
        Bitmap[] crests = {awayCrest, homeCrest};
        for (int k = 0; k < 2; k++) {
            if (crests[k] == null) continue;
            float size = h * 1.25f, cx = k == 0 ? h * 0.32f : w - h * 0.32f, cy = h * 0.55f;
            android.graphics.RectF r = new android.graphics.RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
            android.graphics.ColorMatrix gray = new android.graphics.ColorMatrix();
            gray.setSaturation(0f);
            android.graphics.Paint cp = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
            cp.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFF000000, android.graphics.PorterDuff.Mode.SRC_IN));
            cp.setAlpha(38);
            r.offset(2, 2);
            c.drawBitmap(crests[k], null, r, cp);
            cp.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN));
            cp.setAlpha(26);
            r.offset(-4, -4);
            c.drawBitmap(crests[k], null, r, cp);
            cp.setColorFilter(new android.graphics.ColorMatrixColorFilter(gray));
            cp.setAlpha(46);
            r.offset(2, 2);
            c.drawBitmap(crests[k], null, r, cp);
        }
        // the weave: threads one way and the other, alternately lit and shaded, and a little grain
        android.graphics.Paint t = new android.graphics.Paint();
        t.setStrokeWidth(1f);
        for (int y = 0; y < h; y += 3) {
            t.setColor((y / 3) % 2 == 0 ? 0x12FFFFFF : 0x16000000);
            c.drawLine(0, y, w, y, t);
        }
        for (int x = 0; x < w; x += 3) {
            t.setColor((x / 3) % 2 == 0 ? 0x0CFFFFFF : 0x10000000);
            c.drawLine(x, 0, x, h, t);
        }
        java.util.Random rnd = new java.util.Random(w * 31L + away * 7L + home);
        for (int i = 0; i < w * h / 10; i++) {
            t.setColor(rnd.nextBoolean() ? 0x0DFFFFFF : 0x12000000);
            c.drawPoint(rnd.nextInt(w), rnd.nextInt(h), t);
        }
        // shade under the text
        p.setShader(new android.graphics.LinearGradient(0, 0, 0, h, new int[]{0x66000000, 0x14000000, 0x14000000, 0x5C000000},
                new float[]{0f, 0.35f, 0.65f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new android.graphics.RadialGradient(w / 2f, h * 0.48f, h * 0.55f, 0x52000000, 0x00000000, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        return b;
    }

    /** A team's colour from its crest: the commonest strong hue, deepened so white names read on it. */
    static int teamColour(Bitmap bm) {
        Bitmap s = Bitmap.createScaledBitmap(bm, 24, 24, true);
        int[] count = new int[12];
        float[][] sum = new float[12][3];
        float[] hsv = new float[3];
        for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) {
            int p = s.getPixel(x, y);
            if ((p >>> 24) < 128) continue;
            android.graphics.Color.colorToHSV(p, hsv);
            if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue;
            int b = Math.min(11, (int) (hsv[0] / 30));
            count[b]++;
            sum[b][0] += hsv[0]; sum[b][1] += hsv[1]; sum[b][2] += hsv[2];
        }
        int best = -1;
        for (int b = 0; b < 12; b++) if (count[b] > 8 && (best < 0 || count[b] > count[best])) best = b;
        if (best < 0) return 0xFF3A3A3A;   // a black-and-white crest: a deep grey
        float n = count[best];
        return android.graphics.Color.HSVToColor(new float[]{sum[best][0] / n, Math.min(0.9f, sum[best][1] / n), Math.min(0.6f, sum[best][2] / n)});
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

    // ---------------------------------------------------------------- the live stream inside the card

    private androidx.media3.exoplayer.ExoPlayer player;
    private android.view.TextureView video;
    private final Runnable previewLater = this::startPreview;

    /** the channel in the lens plays inside its card, muted; OK still opens it in Shahid, with sound */
    private void startPreview() {
        if (lensAt < 0) return;
        Poster p = posters.get(lensAt);
        String id = p.item.channelId;
        pool.execute(() -> {
            String url = ShahidLive.stream(id);
            if (url == null) return;
            main.post(() -> { if (lensAt >= 0 && posters.get(lensAt) == p) play(p, url); });
        });
    }

    private void play(Poster p, String url) {
        stopPreview();
        if (player == null) {
            player = new androidx.media3.exoplayer.ExoPlayer.Builder(m).build();
            player.setVolume(0f);
            player.addListener(new androidx.media3.common.Player.Listener() {
                @Override public void onRenderedFirstFrame() {
                    if (video != null) video.animate().alpha(1f).setDuration(450).setInterpolator(EASE).start();
                }
            });
        }
        video = new android.view.TextureView(m);
        video.setAlpha(0f);
        p.view.addView(video, p.view.indexOfChild(p.dim), new FrameLayout.LayoutParams(-1, -1));   // under the dot and the ring
        player.setVideoTextureView(video);
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(url));
        player.prepare();
        player.play();
    }

    void stopPreview() {
        main.removeCallbacks(previewLater);
        if (player != null) { player.stop(); player.clearMediaItems(); if (video != null) player.clearVideoTextureView(video); }
        if (video != null && video.getParent() != null) ((android.view.ViewGroup) video.getParent()).removeView(video);
        video = null;
    }

    void destroy() {
        stopPreview();
        if (player != null) { player.release(); player = null; }
        pool.shutdownNow();
    }

    static Intent parse(Item it) {
        if (it.intent == null) return null;
        try { return Intent.parseUri(it.intent, Intent.URI_INTENT_SCHEME); }
        catch (Exception e) { return null; }
    }
}
