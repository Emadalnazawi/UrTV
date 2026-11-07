import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

/** For tests/render_gone_test.py: RenderGuard (the class of whichever app is compiled into the folder given). One line per check: name TAB ok. */
public final class RenderGuardRun {
    static void check(String name, boolean ok) { System.out.println(name + "\t" + (ok ? "ok" : "FAIL")); }

    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName(args[0]);
        Constructor<?> k = c.getDeclaredConstructor();
        k.setAccessible(true);
        Method gone = c.getDeclaredMethod("gone", long.class), may = c.getDeclaredMethod("mayCreate", long.class);
        gone.setAccessible(true);
        may.setAccessible(true);
        long min = 60_000L;

        Object g = k.newInstance();
        check("nothing died: a view may be made", (boolean) may.invoke(g, 0L));
        gone.invoke(g, 1 * min);
        check("died once: made again", (boolean) may.invoke(g, 1 * min));
        gone.invoke(g, 2 * min);
        check("died twice in ten minutes: made again (the second time)", (boolean) may.invoke(g, 2 * min));
        gone.invoke(g, 3 * min);
        check("died a third time in ten minutes: not made again", !(boolean) may.invoke(g, 3 * min));
        gone.invoke(g, 4 * min);
        gone.invoke(g, 5 * min);
        check("still dying: still not", !(boolean) may.invoke(g, 5 * min));
        check("ten minutes after the first death, one has aged out but three are still in the window: not yet", !(boolean) may.invoke(g, 11 * min + 1));
        check("when enough time has passed the screen may have a view again", (boolean) may.invoke(g, 16 * min));

        Object h = k.newInstance();
        gone.invoke(h, 0L);
        gone.invoke(h, 11 * min);
        gone.invoke(h, 22 * min);
        check("deaths far apart never add up", (boolean) may.invoke(h, 22 * min));
    }
}
