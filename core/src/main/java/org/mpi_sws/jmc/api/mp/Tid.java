package org.mpi_sws.jmc.api.mp;

import org.mpi_sws.jmc.runtime.JmcRuntime;

import java.util.Objects;

public final class Tid {

    private final long raw;

    private Tid(long raw) {
        this.raw = raw;
    }

    static Tid of(long raw) {
        return new Tid(raw);
    }

    public static Tid current() {
        return of(JmcRuntime.currentTask());
    }

    public long raw() {
        return raw;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Tid other && other.raw == this.raw;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(raw);
    }

    @Override
    public String toString() {
        return "Tid(" + raw + ")";
    }
}