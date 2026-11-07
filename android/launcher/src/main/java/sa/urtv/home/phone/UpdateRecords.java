package sa.urtv.home.phone;

/**
 * «تحديث تلفزيوني», the «update in progress» record. Written just before the launcher is replaced (the process that runs the update dies with
 * it), read by the next start of the launcher, shown once, and removed. Two lines: the build the update is going to, and the step. It never
 * claims success itself: success is the installed build having reached the target. No Android in here; Store is a file in the app.
 */
public final class UpdateRecords {
    public interface Store {
        /** The text of the record, or null when there is none. */
        String read();
        /** False when it could not be kept (then the update must not start). */
        boolean write(String text);
        void clear();
    }

    public enum Kind { NONE, SUCCESS, FAILED }

    public static final class Outcome {
        public final Kind kind;
        public final int target;
        public final String step;

        Outcome(Kind kind, int target, String step) {
            this.kind = kind;
            this.target = target;
            this.step = step;
        }
    }

    private static final Outcome NONE = new Outcome(Kind.NONE, 0, "");

    private UpdateRecords() { }

    static String encode(int target, String step) { return "target=" + target + "\nstep=" + step + "\n"; }

    /** Writes the record; false when the store refused. */
    public static boolean begin(Store s, int target, String step) {
        if (target < 0 || step == null || !step.matches("[a-z.]{1,40}")) return false;
        return s.write(encode(target, step));
    }

    /** What the record says, given the build installed now. Nothing is removed. */
    public static Outcome peek(Store s, long installedBuild) {
        String text = s.read();
        if (text == null || text.trim().isEmpty()) return NONE;
        int target = -1;
        String step = "";
        for (String line : text.split("\n")) {
            if (line.startsWith("target=") && line.substring(7).matches("[0-9]{1,9}")) target = Integer.parseInt(line.substring(7));
            else if (line.startsWith("step=") && line.substring(5).matches("[a-z.]{1,40}")) step = line.substring(5);
        }
        if (target < 0 || step.isEmpty()) return new Outcome(Kind.FAILED, 0, "unknown");   // a record we can't read is not a success
        if (installedBuild >= target) return new Outcome(Kind.SUCCESS, target, step);
        return new Outcome(Kind.FAILED, target, step);
    }

    /** As peek, and removes the record: what it says is shown once. */
    public static Outcome take(Store s, long installedBuild) {
        Outcome o = peek(s, installedBuild);
        if (o.kind != Kind.NONE) s.clear();
        return o;
    }
}
