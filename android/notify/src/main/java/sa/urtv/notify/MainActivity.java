package sa.urtv.notify;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/** Status page: is it listening, the TV's address for Home Assistant, overlay permission, and a test. */
public class MainActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        NotifyService.start(this);
        Typeface tf;
        try { tf = Typeface.createFromAsset(getAssets(), "fonts/Thmanyah-Regular.otf"); } catch (RuntimeException e) { tf = Typeface.DEFAULT; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF101418);
        root.setPadding(dp(96), dp(48), dp(96), dp(48));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        title.setTypeface(tf);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        status.setTypeface(tf);
        status.setTextColor(0xCCFFFFFF);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = dp(20);
        root.addView(status, sp);

        Button test = new Button(this);
        test.setText(R.string.test_button);
        test.setTypeface(tf);
        test.setAllCaps(false);
        test.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(24));
        g.setColor(0xFF2E7D6B);
        test.setBackground(g);
        test.setPadding(dp(32), dp(10), dp(32), dp(10));
        test.setOnClickListener(v -> startService(new Intent(this, NotifyService.class).putExtra("test", true)));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = dp(28);
        root.addView(test, tp);
        test.requestFocus();

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean overlay = Settings.canDrawOverlays(this);
        status.setText(getString(R.string.status, ip(), NotifyService.PORT_NFA, NotifyService.PORT_PIPUP)
                + "\n" + getString(overlay ? R.string.overlay_ok : R.string.overlay_missing)
                + (NotifyService.busyPort != 0 ? "\n" + getString(R.string.port_busy, NotifyService.busyPort) : ""));
    }

    private static String ip() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address) return a.getHostAddress();
        } catch (Exception ignored) { }
        return "?";
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
