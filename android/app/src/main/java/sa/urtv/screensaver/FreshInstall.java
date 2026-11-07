package sa.urtv.screensaver;

import android.content.Context;
import android.content.pm.PackageInfo;

/**
 * Did this app come to the TV fresh, never updated over an older version? Only the first-run city note asks (Location.noteDue): an
 * installed TV keeps its look. Kept out of Prefs on purpose: the page is never chosen by install or update times (PageStamp).
 */
final class FreshInstall {
    private FreshInstall() { }

    static boolean is(Context c) {
        try {
            PackageInfo i = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return i.firstInstallTime == i.lastUpdateTime;
        } catch (Exception e) {
            return false;   // not known: no note
        }
    }
}
