package sa.urtv.home;

import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * The home screen's own sounds, one family: a light drop for moving, a fuller one for OK, a smaller falling one
 * for Back, two dull knocks where the remote can go no further, and three rising notes when the settings open.
 * Android's own clicks are switched off in our windows so the two never play together; with the TV's system
 * sounds turned off, these stay silent too.
 */
final class Sounds {
    private static SoundPool pool;
    private static int move, ok, back, edge, open;
    private static final Handler main = new Handler(Looper.getMainLooper());

    static void init(Context c) {
        if (pool != null) return;
        pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        Context a = c.getApplicationContext();
        move = pool.load(a, R.raw.ui_move, 1);
        ok = pool.load(a, R.raw.ui_ok, 1);
        back = pool.load(a, R.raw.ui_back, 1);
        edge = pool.load(a, R.raw.ui_edge, 1);
        open = pool.load(a, R.raw.ui_open, 1);
    }

    private static void play(Context c, int id, float vol) {
        if (pool == null || id == 0) return;
        if (Settings.System.getInt(c.getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 1) == 0) return;
        pool.play(id, vol, vol, 1, 0, 1f);
    }

    static void open(Context c) { play(c, open, 0.7f); }
    static void back(Context c) { play(c, back, 0.6f); }

    /** Android's clicks off for every view in this window, now and for views added later. */
    static void quiet(Activity a) {
        View root = a.getWindow().getDecorView();
        mute(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> mute(root));
    }

    private static void mute(View v) {
        if (v.isSoundEffectsEnabled()) v.setSoundEffectsEnabled(false);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) mute(g.getChildAt(i));
        }
    }

    /**
     * From an activity's dispatchKeyEvent, around super: `handled` is what super returned. An arrow a view
     * handled itself (a choice, the volume) moved something; one it didn't is a focus move, done by Android just
     * after this returns, so the focus is compared a moment later: moved is a drop, stayed is the knock.
     */
    static void key(Activity a, KeyEvent e, boolean handled, View before, boolean quietOk) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return;
        int k = e.getKeyCode();
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
                if (handled) { play(a, move, 0.55f); return; }
                main.post(() -> play(a, a.getCurrentFocus() != before ? move : edge, a.getCurrentFocus() != before ? 0.55f : 0.6f));
                return;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() == 0 && !quietOk && before != null && before.isClickable()) play(a, ok, 0.7f);
                return;
            default:
        }
    }
}
