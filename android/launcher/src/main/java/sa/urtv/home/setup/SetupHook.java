package sa.urtv.home.setup;

import android.content.Context;
import android.widget.Toast;

/**
 * The one door the home screen uses (a single call in MainActivity.onCreate): after a Force-stop or a restart, pressing Home starts OUR home, and a home
 * switch that was cut off must not be forgotten. If the journal says «switching», the safety switch is set again from its saved deadline and one attempt
 * to put the stock home back is made on a background thread. After too many failed attempts a notice is shown at every start until it is dealt with.
 * Never throws: it must not be able to break the home screen.
 */
public final class SetupHook {
    private SetupHook() { }

    public static void atHomeStart(final Context c) {
        try {
            final Context app = c.getApplicationContext();
            if (!SetupRecovery.pending(app)) return;
            SetupRecovery.rearmFromDisk(app);
            if (SetupRecovery.gaveUp(app)) {
                Toast.makeText(app, SetupRecovery.GAVE_UP_NOTICE, Toast.LENGTH_LONG).show();
                return;
            }
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        SetupRecovery.attempt(app, new SetupEngine.Out() { @Override public void line(String text) { } });
                    } catch (RuntimeException ignored) { }
                }
            }, "setup-home-start").start();
        } catch (RuntimeException ignored) {
            // the home screen comes first
        }
    }
}
