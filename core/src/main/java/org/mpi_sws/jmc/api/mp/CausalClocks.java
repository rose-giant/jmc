package org.mpi_sws.jmc.api.mp;

final class CausalClocks {

    private static final ThreadLocal<VectorClock> CLOCK = ThreadLocal.withInitial(VectorClock::new);

    private CausalClocks() {}

    static VectorClock tickAndGet() {
        Tid self = Tid.current();
        VectorClock next = CLOCK.get().withTick(self);
        CLOCK.set(next);
        return next;
    }

    static void observe(VectorClock incoming) {
        CLOCK.set(CLOCK.get().joinedWith(incoming));
    }

    static void resetCurrentThread() {
        CLOCK.remove();
    }
}