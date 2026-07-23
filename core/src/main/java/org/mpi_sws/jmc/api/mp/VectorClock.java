package org.mpi_sws.jmc.api.mp;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class VectorClock {

    private final Map<Long, Long> ticks;

    VectorClock() {
        this.ticks = Map.of();
    }

    private VectorClock(Map<Long, Long> ticks) {
        this.ticks = ticks;
    }

    long get(Tid tid) {
        return ticks.getOrDefault(tid.raw(), 0L);
    }

    VectorClock withTick(Tid tid) {
        Map<Long, Long> copy = new HashMap<>(ticks);
        copy.merge(tid.raw(), 1L, Long::sum);
        return new VectorClock(copy);
    }

    VectorClock joinedWith(VectorClock other) {
        Map<Long, Long> copy = new HashMap<>(ticks);
        other.ticks.forEach((tid, value) -> copy.merge(tid, value, Math::max));
        return new VectorClock(copy);
    }

    boolean leq(VectorClock other) {
        for (Map.Entry<Long, Long> entry : ticks.entrySet()) {
            if (entry.getValue() > other.ticks.getOrDefault(entry.getKey(), 0L)) {
                return false;
            }
        }
        return true;
    }

    boolean lessThan(VectorClock other) {
        return this.leq(other) && !this.ticks.equals(other.ticks);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VectorClock other && this.ticks.equals(other.ticks);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(ticks);
    }

    @Override
    public String toString() {
        return ticks.toString();
    }
}