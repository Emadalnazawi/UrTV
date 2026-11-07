package sa.urtv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The TV has started again: the sleep timer's alarm is gone with the old boot, so its saved end is forgotten (SleepTimer), and a cut-short Family DNS check is undone. Not exported to apps. */
public class SleepBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        SleepTimer.forgetAfterBoot(c);
        FamilyDnsAndroid.recover(c);   // a Family DNS check cut short by the reboot: the old setting goes back (no polling, no network)
    }
}
