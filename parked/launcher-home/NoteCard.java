package sa.urtv.home;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A pop-up from «تنبيهات تلفزيونك» (Home Assistant) shown in the home card's place, in the screensaver's card
 * style (.week in src/template.html): dark glass, a thin gold border, a gold heading with a fading gold rule.
 * It covers the optional home card while it shows, then goes away by itself.
 */
final class NoteCard {
    private static final int BG = 0x99040E09, BORDER = 0x29DCB86F, GOLD = 0xFFD6AF6B, TEXT = 0xFFF6EEDC, SUB = 0xFFC9BFA6;

    private final Activity activity;
    private final ViewGroup parent;
    private final FrameLayout.LayoutParams params;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::hide;
    private View shown;
    private Runnable after;
    private Typeface regular, medium;

    NoteCard(Activity activity, ViewGroup parent, FrameLayout.LayoutParams params) {
        this.activity = activity;
        this.parent = parent;
        this.params = params;
        try {
            regular = Typeface.createFromAsset(activity.getAssets(), "fonts/Thmanyah-Regular.otf");
            medium = Typeface.createFromAsset(activity.getAssets(), "fonts/Thmanyah-Medium.otf");
        } catch (RuntimeException e) { regular = Typeface.DEFAULT; medium = Typeface.DEFAULT_BOLD; }
    }

    /** {@code after} runs once the card has gone (the home card comes back then). */
    void show(String title, String message, byte[] image, int seconds, Runnable after) {
        boolean replacing = shown != null;
        hide(false);
        this.after = after;
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(16), dp(22), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(BG);
        bg.setStroke(dp(1), BORDER);
        card.setBackground(bg);

        // heading: «تنبيه · 12:28 م» then a gold rule fading to the left, as .week .hd
        LinearLayout hd = new LinearLayout(activity);
        hd.setOrientation(LinearLayout.HORIZONTAL);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        View rule = new View(activity);
        GradientDrawable rg = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{0x80DCB86F, 0x00DCB86F});
        rule.setBackground(rg);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(1), 1f);
        rp.rightMargin = dp(12);
        hd.addView(rule, rp);
        TextView label = text("تنبيه · " + now(), 13, medium, GOLD);
        label.setLetterSpacing(0.12f);
        hd.addView(label, new LinearLayout.LayoutParams(-2, -2));
        card.addView(hd, new LinearLayout.LayoutParams(-1, -2));

        // text at the right; a picture, if any, at its left, so the card keeps the home card's height
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        Bitmap b = image == null ? null : BitmapFactory.decodeByteArray(image, 0, image.length);
        if (b != null) {
            ImageView iv = new ImageView(activity);
            iv.setImageBitmap(b);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable r = new GradientDrawable();
            r.setCornerRadius(dp(8));
            r.setColor(0xFF000000);
            iv.setBackground(r);
            iv.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            iv.setClipToOutline(true);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(224), dp(126));
            p.rightMargin = dp(16);
            body.addView(iv, p);
        }
        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        if (title != null && !title.isEmpty()) texts.addView(text(title, b != null ? 19 : 22, medium, TEXT));
        if (message != null && !message.isEmpty()) texts.addView(text(message, 16, regular, SUB));
        body.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(10);
        card.addView(body, bp);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(params);
        lp.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        // in: a soft drop and settle; a new note over an old one only cross-fades
        card.setAlpha(0f);
        card.setTranslationY(replacing ? 0 : -dp(14));
        card.setScaleX(replacing ? 1f : 0.97f);
        card.setScaleY(replacing ? 1f : 0.97f);
        card.setPivotY(0);
        parent.addView(card, lp);
        card.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f)
                .setStartDelay(replacing ? 0 : 120).setDuration(420)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2.2f)).start();
        shown = card;
        main.postDelayed(hide, Math.max(3, Math.min(600, seconds)) * 1000L);
    }

    void hide() { hide(true); }

    private void hide(boolean restore) {
        main.removeCallbacks(hide);
        if (shown == null) return;
        View v = shown;
        shown = null;
        Runnable a = after;
        after = null;
        v.animate().alpha(0f).translationY(-dp(8)).setStartDelay(0).setDuration(260)
                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f))
                .withEndAction(() -> parent.removeView(v)).start();
        if (restore && a != null) main.postDelayed(a, 140);
    }

    private TextView text(String s, float sp, Typeface tf, int color) {
        TextView t = new TextView(activity);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setGravity(Gravity.RIGHT);
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private static String now() {
        Date d = new Date();
        String hm = new SimpleDateFormat("h:mm", Locale.US).format(d);
        int h = Integer.parseInt(new SimpleDateFormat("H", Locale.US).format(d));
        return hm + (h < 12 ? " ص" : " م");
    }

    private int dp(int v) { return Math.round(v * activity.getResources().getDisplayMetrics().density); }
}
