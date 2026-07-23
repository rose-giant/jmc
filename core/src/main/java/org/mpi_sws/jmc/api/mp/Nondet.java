package org.mpi_sws.jmc.api.mp;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.PrimitiveValue;

import java.util.List;
import java.util.Random;

public final class Nondet {

    private Nondet() {}

    public static <T> T choose(List<T> options) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("nondet() over an empty set of options");
        }
        if (options.size() == 1) {
            return options.get(0);
        }
        return options.get(chooseIndex(options.size()));
    }

    public static boolean chooseBoolean() {
        return choose(List.of(Boolean.TRUE, Boolean.FALSE));
    }

    public static int chooseIndex(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive, was " + bound);
        }
        if (bound == 1) {
            return 0;
        }
        int bits = 32 - Integer.numberOfLeadingZeros(bound - 1);
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.REACTIVE_EVENT_RANDOM_VALUE)
                        .taskId(JmcRuntime.currentTask())
                        .param("bits", bits)
                        .build();
        Object result = JmcRuntime.updateEventAndYield(event);
        int raw = (result instanceof PrimitiveValue pv) ? pv.asInteger() : new Random().nextInt();
        return (raw & Integer.MAX_VALUE) % bound;
    }
}