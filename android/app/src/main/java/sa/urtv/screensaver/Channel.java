package sa.urtv.screensaver;

/**
 * The page's fonts and photos are always master URLs (src/build.py), also in a page built from next, and a build from
 * next serves from next's prefix. This maps one to the other, so both channels' pages are answered from the APK.
 */
final class Channel {
    static final String MASTER = "https://raw.githubusercontent.com/tshi-dev/UrTV/master/";

    private Channel() {}

    /** the URL under this build's own prefix `repo` if it was under master's, else unchanged */
    static String own(String url, String repo) {
        return url.startsWith(MASTER) ? repo + url.substring(MASTER.length()) : url;
    }
}
