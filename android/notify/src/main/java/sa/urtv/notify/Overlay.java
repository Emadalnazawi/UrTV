package sa.urtv.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Shows one Note at a time over whatever is on screen; a new one replaces the old. */
final class Overlay {
    private final Context ctx;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private View shown;
    private ExoPlayer player;
    private final Runnable hide = this::hide;
    private Typeface regular, medium;
    // a soft two-note chime with each pop-up (res/raw/chime.wav, made for this app), at a third of the TV's volume
    private final android.media.SoundPool sounds = new android.media.SoundPool.Builder().setMaxStreams(1)
            .setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
    private int chime;

    Overlay(Context c) {
        ctx = c.getApplicationContext();
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        chime = sounds.load(ctx, R.raw.chime, 1);
        try {
            regular = Typeface.createFromAsset(ctx.getAssets(), "fonts/Thmanyah-Regular.otf");
            medium = Typeface.createFromAsset(ctx.getAssets(), "fonts/Thmanyah-Medium.otf");
        } catch (RuntimeException e) { regular = Typeface.DEFAULT; medium = Typeface.DEFAULT_BOLD; }
    }

    /**
     * Safe from any thread. While «تلفزيونك» (the home screen) is in front it shows text and pictures in its
     * own card style; it answers the ordered broadcast with 1. Otherwise (an app, the screensaver), or for
     * video, the pop-up goes over the screen here.
     */
    void show(Note n) {
        main.post(() -> {
            if (n.sound) sounds.play(chime, 0.35f, 0.35f, 1, 0, 1f);
            if (n.videoUrl != null) { showNow(n); return; }
            Intent i = new Intent("sa.urtv.home.SHOW_NOTE").setPackage("sa.urtv.home")
                    .putExtra("title", n.title).putExtra("message", n.message).putExtra("seconds", n.seconds);
            byte[] img = small(n.image);
            if (img != null) i.putExtra("image", img);
            // the home screen first; then the screensaver; then a pop-up of our own
            ctx.sendOrderedBroadcast(i, null, new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent r) {
                    if (getResultCode() == 1) return;
                    ctx.sendOrderedBroadcast(new Intent(i).setPackage("sa.urtv.screensaver"), null, new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context c2, Intent r2) { if (getResultCode() != 1) showNow(n); }
                    }, main, 0, null, null);
                }
            }, main, 0, null, null);
        });
    }

    /** A picture small enough for a broadcast (they cap near 1 MB): JPEG, at most 960 px wide. */
    private static byte[] small(byte[] b) {
        if (b == null) return null;
        if (b.length < 300_000) return b;
        Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length);
        if (bm == null) return null;
        if (bm.getWidth() > 960) bm = Bitmap.createScaledBitmap(bm, 960, bm.getHeight() * 960 / bm.getWidth(), true);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 80, out);
        return out.size() < 700_000 ? out.toByteArray() : null;
    }

    private void showNow(Note n) {
        hide();
        boolean rtl = n.isArabic();

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        card.setPadding(dp(20), dp(16), dp(20), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(withAlpha(n.color, Math.max(0.15f, n.alpha * 0.92f)));
        bg.setStroke(dp(1), 0x22FFFFFF);
        card.setBackground(bg);
        card.setElevation(dp(8));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (n.icon != null) {
            Bitmap ic = BitmapFactory.decodeByteArray(n.icon, 0, n.icon.length);
            if (ic != null) {
                ImageView iv = new ImageView(ctx);
                iv.setImageBitmap(ic);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(44), dp(44));
                p.setMarginEnd(dp(14));
                row.addView(iv, p);
            }
        }
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        if (!n.title.isEmpty()) texts.addView(text(n.title, 20 * n.textScale, medium, Color.WHITE, rtl));
        if (!n.message.isEmpty()) texts.addView(text(n.message, 17 * n.textScale, regular, 0xDDFFFFFF, rtl));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        if (texts.getChildCount() > 0 || row.getChildCount() > 1) card.addView(row);

        int mediaW = Math.max(dp(160), Math.min(n.mediaWidthPx, ctx.getResources().getDisplayMetrics().widthPixels * 2 / 3));
        if (n.image != null) addPicture(card, BitmapFactory.decodeByteArray(n.image, 0, n.image.length), mediaW);
        else if (n.imageUrl != null) fetchPicture(card, n.imageUrl, mediaW);
        if (n.videoUrl != null) addVideo(card, n.videoUrl, mediaW);

        int width = (n.image != null || n.imageUrl != null || n.videoUrl != null) ? mediaW + dp(40) : dp(520);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = gravity(n.position);
        lp.x = dp(48);
        lp.y = dp(40);
        lp.windowAnimations = android.R.style.Animation_Toast;
        try {
            wm.addView(card, lp);
            shown = card;
            main.postDelayed(hide, n.seconds * 1000L);
        } catch (RuntimeException e) {
            Log.e("UrTVNotify", "can't show the pop-up", e);   // usually: no overlay permission yet (MainActivity says so)
            releasePlayer();
        }
    }

    void hide() {
        main.removeCallbacks(hide);
        releasePlayer();
        if (shown != null) {
            try { wm.removeView(shown); } catch (RuntimeException ignored) { }
            shown = null;
        }
    }

    private void addPicture(LinearLayout card, Bitmap b, int w) {
        if (b == null) return;
        ImageView iv = new ImageView(ctx);
        iv.setImageBitmap(b);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setClipToOutline(true);
        iv.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        GradientDrawable r = new GradientDrawable();
        r.setCornerRadius(dp(12));
        r.setColor(Color.BLACK);
        iv.setBackground(r);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, -2);
        p.topMargin = card.getChildCount() > 0 ? dp(12) : 0;
        card.addView(iv, p);
    }

    private void fetchPicture(LinearLayout card, String url, int w) {
        new Thread(() -> {
            Bitmap b = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(15000);
                try (InputStream in = c.getInputStream()) { b = BitmapFactory.decodeStream(in); }
            } catch (Exception ignored) { }
            Bitmap fb = b;
            main.post(() -> { if (shown == card) addPicture(card, fb, w); });
        }).start();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void addVideo(LinearLayout card, String url, int w) {
        releasePlayer();
        player = new ExoPlayer.Builder(ctx).build();
        Uri u = Uri.parse(url);
        if ("rtsp".equalsIgnoreCase(u.getScheme())) {
            // TCP: the eufy cameras' RTSP drops UDP packets over Wi-Fi
            player.setMediaSource(new RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(MediaItem.fromUri(u)));
        } else {
            player.setMediaItem(MediaItem.fromUri(u));
        }
        player.setVolume(0f);
        player.prepare();
        player.play();
        PlayerView pv = new PlayerView(ctx);
        pv.setUseController(false);
        pv.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        pv.setPlayer(player);
        pv.setClipToOutline(true);
        GradientDrawable r = new GradientDrawable();
        r.setCornerRadius(dp(12));
        r.setColor(Color.BLACK);
        pv.setBackground(r);
        FrameLayout box = new FrameLayout(ctx);
        box.addView(pv, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, w * 9 / 16);
        p.topMargin = card.getChildCount() > 0 ? dp(12) : 0;
        card.addView(box, p);
    }

    private void releasePlayer() {
        if (player != null) { player.release(); player = null; }
    }

    private TextView text(String s, float sp, Typeface tf, int color, boolean rtl) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        t.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        t.setTextDirection(rtl ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        return t;
    }

    private static int gravity(int position) {
        switch (position) {
            case Note.BOTTOM_LEFT: return Gravity.BOTTOM | Gravity.LEFT;
            case Note.TOP_RIGHT: return Gravity.TOP | Gravity.RIGHT;
            case Note.TOP_LEFT: return Gravity.TOP | Gravity.LEFT;
            case Note.CENTER: return Gravity.CENTER;
            default: return Gravity.BOTTOM | Gravity.RIGHT;
        }
    }

    private int dp(int v) { return Math.round(v * ctx.getResources().getDisplayMetrics().density); }

    private static int withAlpha(int c, float a) {
        return Color.argb(Math.round(a * 255), Color.red(c), Color.green(c), Color.blue(c));
    }
}
