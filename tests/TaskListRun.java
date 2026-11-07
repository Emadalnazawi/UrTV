package sa.urtv.screensaver;

import java.util.ArrayList;
import java.util.List;

/** For tests/task_ids_test.py: TaskList.java, two tasks that say the same thing, and a list that moves. Prints one line per check: name TAB ok. */
public final class TaskListRun {
    static void check(String name, boolean ok) { System.out.println(name + "\t" + (ok ? "ok" : "FAIL")); }

    static List<Task> two() {
        List<Task> v = new ArrayList<>();
        v.add(new Task("اشتر خبز", false, 100, 0));
        v.add(new Task("اشتر خبز", false, 200, 0));
        return v;
    }

    public static void main(String[] args) {
        List<Task> old = two();
        check("a list from before ids needs them", TaskList.needsIds(old));
        List<Task> v = TaskList.withIds(old, 0);
        check("ids are given in order, nothing else changes",
                v.size() == 2 && v.get(0).id == 1 && v.get(1).id == 2 && v.get(0).at == 100 && v.get(1).at == 200
                && v.get(0).text.equals(v.get(1).text) && !v.get(0).done && !TaskList.needsIds(v));
        check("ids already there are kept", TaskList.withIds(v, 0).get(1).id == 2);
        List<Task> dup = new ArrayList<>(v);
        dup.set(1, new Task("x", false, 1, 1));
        check("two with one id are told apart", TaskList.needsIds(dup) && TaskList.withIds(dup, 0).get(1).id == 2);

        List<Task> done = TaskList.markDone(v, v.get(1).id);
        check("done on the second marks the second, not the first", done != null && !done.get(0).done && done.get(1).done);
        check("the list it was given is not changed", !v.get(1).done);
        List<Task> gone = TaskList.remove(v, v.get(1).id);
        check("delete on the second removes the second, not the first", gone != null && gone.size() == 1 && gone.get(0).id == 1 && gone.get(0).at == 100);
        List<Task> moved = TaskList.remove(v, 1);   // the list moved: the other is now first
        check("the id follows the task when the list moves", TaskList.markDone(moved, 2) != null && TaskList.indexOf(moved, 2) == 0);
        check("an id that is gone is refused", TaskList.markDone(moved, 1) == null && TaskList.remove(moved, 1) == null && TaskList.markDone(moved, 0) == null);

        long last = TaskList.lastId(v, 7);
        List<Task> added = TaskList.add(v, "new", 300, last + 1, 12);
        check("a new task gets a new id and goes last", added.size() == 3 && added.get(2).id == 8 && !added.get(2).done);

        List<Task> full = new ArrayList<>();
        for (int k = 1; k <= 12; k++) full.add(new Task("t" + k, false, k, k));
        check("full of open tasks: refused", TaskList.add(full, "n", 1, 13, 12) == null);
        full.set(3, new Task("t4", true, 4, 4));
        List<Task> room = TaskList.add(full, "n", 1, 13, 12);
        check("a done task makes room", room != null && room.size() == 12 && TaskList.indexOf(room, 4) < 0 && room.get(11).id == 13);
        check("ids are never given out twice", TaskList.lastId(room, 0) == 13);
    }
}
