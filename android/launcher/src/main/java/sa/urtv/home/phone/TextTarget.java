package sa.urtv.home.phone;

/** A screen of ours that takes the text typed on the phone as its own input (today «المهام»: the text becomes a task). */
public interface TextTarget {
    /** Takes the text; false when this screen has nothing to put it in right now. Called on the main thread. */
    boolean acceptText(String text);
}
