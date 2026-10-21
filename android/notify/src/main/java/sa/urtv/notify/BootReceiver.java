package sa.urtv.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts listening again after a restart or an update. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) { NotifyService.start(c); }
}
