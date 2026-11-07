package sa.urtv.home.phone;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.lang.ref.WeakReference;

/**
 * Which of our own screens is in front, so a key from the phone can be delivered to it. Android lets an app deliver key events to its own
 * windows only; when another app is in front there is no screen of ours and the remote keys are refused (the page says so). Tracking
 * runs only while the phone control is on.
 */
final class Foreground implements Application.ActivityLifecycleCallbacks {
    private static Foreground active;
    private WeakReference<Activity> current = new WeakReference<>(null);

    static synchronized void track(Application app, Activity now) {
        if (active != null) return;
        active = new Foreground();
        if (now != null) active.current = new WeakReference<>(now);
        app.registerActivityLifecycleCallbacks(active);
    }

    static synchronized void untrack(Application app) {
        if (active == null) return;
        app.unregisterActivityLifecycleCallbacks(active);
        active = null;
    }

    /** The resumed screen of ours, or null when another app (or nothing of ours) is in front. */
    static synchronized Activity current() {
        return active == null ? null : active.current.get();
    }

    @Override public void onActivityResumed(Activity a) { current = new WeakReference<>(a); }

    @Override public void onActivityPaused(Activity a) {
        if (current.get() == a) current = new WeakReference<>(null);
    }

    @Override public void onActivityCreated(Activity a, Bundle b) { }
    @Override public void onActivityStarted(Activity a) { }
    @Override public void onActivityStopped(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
    @Override public void onActivityDestroyed(Activity a) { }
}
