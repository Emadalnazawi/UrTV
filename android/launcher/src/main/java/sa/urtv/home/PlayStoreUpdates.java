package sa.urtv.home;

/**
 * The home screen's evidence of waiting Play Store updates: UNKNOWN, always, so the bell never shows.
 *
 * Checked on API 26..34 with install-time permissions only: the Play Store publishes no count, content provider or broadcast for its
 * updates, PackageManager cannot tell an installed version from the store's newest, and reading the store's notifications (what Matinee
 * and talkies do) needs a notification listener the person grants by hand in the TV's settings, plus a service that stays bound. That is a
 * new always-on service and a manual step on every TV, so it is not done here. When a source that needs nothing special is found, it
 * replaces the body of read(); the bell, its settings row, the lock and the focus walk are already in place.
 */
final class PlayStoreUpdates implements UpdatesBell.Source {
    @Override public UpdatesBell.Evidence read() { return UpdatesBell.Evidence.UNKNOWN; }
}
