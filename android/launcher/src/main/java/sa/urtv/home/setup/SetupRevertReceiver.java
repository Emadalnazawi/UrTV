package sa.urtv.home.setup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * The safety switch fired: if a home switch was left half done and Home is not ours, the stock home is put back (SetupRecovery). One short
 * attempt; the next alarm is set before it starts, so a cut-short attempt is tried again (SetupEngine.recoverIfCutOff keeps «switching»
 * until the stock home is checked).
 */
public class SetupRevertReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    SetupRecovery.attempt(app, new SetupEngine.Out() { @Override public void line(String text) { } });
                } finally {
                    pending.finish();
                }
            }
        }, "setup-revert").start();
    }
}
