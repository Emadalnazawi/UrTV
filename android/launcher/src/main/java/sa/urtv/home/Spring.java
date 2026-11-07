package sa.urtv.home;

// Adapted from talkies (https://github.com/rajupp/talkies, Spring.java, MIT, (c) 2026 TechPP), which has it from Free My TV
// (https://github.com/simbasjungles/free-my-tv, app/src/io/freemytv/Spring.java, MIT, (c) 2026 Free My TV contributors).
// See THIRD_PARTY_NOTICES.md.

/**
 * A damped spring for the home's motion: {@code response} in seconds, {@code damping} 1 for no bounce. Unlike an animator, a new
 * target mid-flight keeps the current speed, so a held arrow glides from card to card instead of starting from a standstill at
 * each one. No Android in here: tests/SpringRun.java runs it.
 */
final class Spring {
    float value, velocity, target;
    private final float response, damping, eps;

    /** For scales and fractions (settles within 0.0008). */
    Spring(float response, float damping, float initial) { this(response, damping, initial, 0.0008f); }

    /** {@code eps}: close enough to stop, in the value's own unit (0.25 for pixels). */
    Spring(float response, float damping, float initial, float eps) {
        this.response = response;
        this.damping = damping;
        this.eps = eps;
        value = target = initial;
    }

    /** Straight to {@code v}, standing still (no motion wanted, or the value was moved by something else). */
    void snap(float v) {
        value = target = v;
        velocity = 0;
    }

    /** Advances {@code dt} seconds; true while still moving. */
    boolean step(float dt) {
        if (settled()) {
            value = target;
            velocity = 0;
            return false;
        }
        float omega = (float) (2 * Math.PI / response);
        float stiffness = omega * omega, friction = 2 * damping * omega;
        int steps = Math.max(1, (int) Math.ceil(dt / 0.004f));   // small steps: a slow frame does not make it unstable
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            float accel = -stiffness * (value - target) - friction * velocity;
            velocity += accel * h;
            value += velocity * h;
        }
        return true;
    }

    boolean settled() {
        return Math.abs(value - target) < eps && Math.abs(velocity) < eps * 5;
    }
}
