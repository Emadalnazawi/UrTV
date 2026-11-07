package sa.urtv.home.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * The answer from Android's installer for «تحديث تلفزيونك» (not exported: only reached through the app's own PendingIntent). The answer starts
 * the next install (an APK parse, a signature check and a copy of up to 80 MB), so it runs on a worker thread: the main thread is not held.
 */
public class PhoneInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, final Intent intent) {
        if (intent == null || !PhoneUpdater.ACTION_RESULT.equals(intent.getAction())) return;
        final Context app = context.getApplicationContext();
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    PhoneUpdater.onResult(app, intent);
                } finally {
                    pending.finish();
                }
            }
        }, "tv-update-result").start();
    }
}
