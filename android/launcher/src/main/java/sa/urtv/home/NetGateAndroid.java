package sa.urtv.home;

import android.content.Context;
import android.os.PowerManager;

/** The Android half of {@link NetGate}: whether the display is on. */
public final class NetGateAndroid {
    private NetGateAndroid() { }

    /** PowerManager.isInteractive(): false in standby (the display is off). When it cannot be read, true: a fetch is not lost for that. */
    public static boolean displayOn(Context c) {
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return pm == null || pm.isInteractive();
        } catch (RuntimeException e) {
            return true;
        }
    }
}
