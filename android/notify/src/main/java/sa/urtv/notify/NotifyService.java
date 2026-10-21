package sa.urtv.notify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Listens on the TV for pop-ups:
 *   7676  Home Assistant's «Notifications for Android TV / Fire TV» (multipart form, icon in «filename»,
 *         picture in «filename2»); a plain GET answers 200 for its connection test
 *   7979  PiPup's JSON on POST /notify (title, message, duration, position, media.image / media.video)
 */
public class NotifyService extends Service {
    static final int PORT_NFA = 7676, PORT_PIPUP = 7979;
    private Http nfa, pipup;
    // a port another app already holds (the store's «Notifications for Android TV» takes 7676)
    static volatile int busyPort;
    private Overlay overlay;

    static void start(Context c) {
        Intent i = new Intent(c, NotifyService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("run", getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "run")
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.running))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
        startForeground(1, n);
        overlay = new Overlay(this);
        try {
            nfa = new Http(PORT_NFA, r -> {
                if (!"POST".equals(r.method)) return;   // GET: Home Assistant checking the TV is there
                Log.i("UrTVNotify", "7676: fields " + r.fields.keySet() + " files " + r.files.keySet());
                overlay.show(Note.fromForm(r.fields, r.files));
            });
        } catch (IOException e) { busyPort = PORT_NFA; }
        try {
            pipup = new Http(PORT_PIPUP, r -> {
                if (!"POST".equals(r.method)) return;
                if (r.path.startsWith("/cancel")) { overlay.hide(); return; }
                overlay.show(Note.fromPiPup(new JSONObject(new String(r.body, StandardCharsets.UTF_8))));
            });
        } catch (IOException e) { busyPort = PORT_PIPUP; }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("test", false)) {
            Note n = new Note();
            n.title = getString(R.string.test_title);
            n.message = getString(R.string.test_message);
            n.seconds = 8;
            overlay.show(n);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (nfa != null) nfa.close();
        if (pipup != null) pipup.close();
        if (overlay != null) overlay.hide();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
