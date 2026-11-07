package sa.urtv.home.phone;

/**
 * The hook for the parental PIN (it lives on work/parental, not here). While required() is true, changing a setting from the phone
 * ("set" and "update install") must carry the PIN, and verify() decides. The parental branch calls PhoneControl.setPinGate with its own
 * implementation; until then there is no PIN and nothing is asked. A wrong PIN five times ends that phone's session.
 */
public interface PinGate {
    boolean required();

    boolean verify(String pin);

    PinGate NONE = new PinGate() {
        @Override public boolean required() { return false; }
        @Override public boolean verify(String pin) { return true; }
    };
}
