package sa.urtv.screensaver;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** For tests/channel_test.py: every asset URL of the built pages is served from the build's own prefix, on both channels. */
public final class ChannelRun {
    static int failed;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  -> " + detail));
        if (!ok) failed++;
    }

    public static void main(String[] args) throws Exception {
        String page = new String(Files.readAllBytes(Paths.get(args[0])), "UTF-8");
        Matcher m = Pattern.compile(Pattern.quote(Channel.MASTER) + "(assets/fonts/|assets/backgrounds/|photos/)[A-Za-z0-9_.-]+").matcher(page);
        for (String branch : new String[] {"master", "next"}) {
            String repo = "https://raw.githubusercontent.com/tshi-dev/UrTV/" + branch + "/";
            int n = 0, bad = 0;
            m.reset();
            while (m.find()) {
                n++;
                String own = Channel.own(m.group(), repo);
                if (!own.startsWith(repo + m.group(1))) { bad++; System.out.println(own); }
            }
            check(branch + " build: " + n + " asset URLs of the page all under its prefix", n > 0 && bad == 0, bad + " not");
        }
        String next = "https://raw.githubusercontent.com/tshi-dev/UrTV/next/";
        check("the next prefix is left alone", Channel.own(next + "photos/1.jpg", next).equals(next + "photos/1.jpg"), "");
        check("other hosts are left alone", Channel.own("https://example.com/photos/1.jpg", next).equals("https://example.com/photos/1.jpg"), "");
        System.exit(failed == 0 ? 0 : 1);
    }
}
