package sa.urtv.home.phone;

import sa.urtv.home.PinStore;

/**
 * The phone's PIN gate over the family lock's own PinStore (Parental.pin): while a PIN is set, "set" and "update install" from the phone must
 * carry it. It is the SAME store the TV's keypad asks, so the 5-wrong-tries lockout (1, 5, 15 minutes) is shared in both directions: wrong
 * tries from a phone lock the keypad on the TV, a lock the TV earned turns the phone's tries away without looking at them, and a new
 * phone session never gets a fresh set of tries. No Android in it; the clock comes in.
 */
public final class PinStoreGate implements PinGate {
    public interface Clock { long now(); }

    private final PinStore store;
    private final Clock clock;

    public PinStoreGate(PinStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override public boolean required() { return store.isSet(); }

    /** True only for the right PIN while the store is not locked (PinStore.verify turns a locked try away without checking it). */
    @Override public boolean verify(String pin) {
        return pin != null && store.verify(pin, clock.now()).kind == PinStore.OK;
    }
}
