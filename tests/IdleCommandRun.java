package sa.urtv.home.tilawah;

public class IdleCommandRun {
    public static void main(String[] a) {
        // mayLeaveNothing, sourceNone, hasPlayer, sleepUntil
        System.out.println(IdleCommand.leavesNothing(true, true, false, 0));         // a pause with nothing loaded -> true
        System.out.println(IdleCommand.leavesNothing(true, false, true, 0));         // something plays -> false
        System.out.println(IdleCommand.leavesNothing(true, true, false, 99));        // only the sleep timer is armed -> false
        System.out.println(IdleCommand.leavesNothing(false, true, false, 0));        // radio / adhan / stop: their own course -> false
        System.out.println(IdleCommand.leavesNothing(true, false, false, 0));        // a source is chosen (loading) -> false
        System.out.println(IdleCommand.leavesNothing(true, true, true, 0));          // a player exists -> false
    }
}
