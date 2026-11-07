package sa.urtv.home.setup;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * The in-app safety switch of the home switch: an alarm that fires {@link SetupRevertReceiver} after a few minutes, which puts the stock home back
 * unless the switch was confirmed (disarm). It lives in this app, not in a background job started over ADB (some TVs end those with the session).
 * An alarm is lost when the TV restarts or the app is force-stopped: the deadline is also on disk (home-deadline.txt) and {@link SetupBootReceiver}
 * and the setup screen set it again (see {@link SetupRecovery}).
 */
final class SetupDeadman implements SetupEngine.Deadman {
    private final Context context;
    private boolean exact = true;

    SetupDeadman(Context c) { context = c.getApplicationContext(); }

    private PendingIntent intent() {
        Intent i = new Intent(context, SetupRevertReceiver.class);
        return PendingIntent.getBroadcast(context, 71, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** False when no alarm could be set at all: the home switch then does not start. */
    @Override public boolean arm(int seconds) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;
        long at = System.currentTimeMillis() + seconds * 1000L;
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                exact = false;   // Android 12 and newer without the exact-alarm permission: it can be minutes late
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent());
            } else {
                exact = true;
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent());
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override public boolean exact() { return exact; }

    @Override public void disarm() {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(intent());
    }
}
