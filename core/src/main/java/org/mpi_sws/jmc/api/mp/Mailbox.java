package org.mpi_sws.jmc.api.mp;

import org.mpi_sws.jmc.api.JmcObject;
import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class Mailbox {

    private final List<Envelope> pending = new ArrayList<>();

    private <R> R withLock(Supplier<R> body) {
        JmcRuntimeUtils.syncBlockLock(this);
        try {
            return body.get();
        } finally {
            JmcRuntimeUtils.syncBlockUnLock(this);
        }
    }

    void deposit(Envelope envelope) {
        withLock(
                () -> {
                    pending.add(envelope);
                    JmcObject.objectNotifyAll(this);
                    return null;
                });
    }

    Envelope receiveBlocking(CommunicationModel model, Predicate<Object> guard) {
        return withLock(
                () -> {
                    Envelope chosen;
                    while ((chosen = tryMatchLocked(model, guard)) == null) {
                        MpEvent.blocked(Tid.current(), model);
                        try {
                            JmcObject.objectWait(this);
                        } catch (InterruptedException e) {
                            throw new RuntimeException("Interrupted while blocked on a Must receive", e);
                        }
                    }
                    return chosen;
                });
    }

    Optional<Envelope> receiveNonBlocking(CommunicationModel model, Predicate<Object> guard) {
        return withLock(() -> Optional.ofNullable(tryMatchLocked(model, guard)));
    }

    private Envelope tryMatchLocked(CommunicationModel model, Predicate<Object> guard) {
        List<Envelope> candidates = computeCandidates(model, guard);
        if (candidates.isEmpty()) {
            return null;
        }
        Envelope chosen = candidates.size() == 1 ? candidates.get(0) : Nondet.choose(candidates);
        pending.remove(chosen);
        return chosen;
    }

    private List<Envelope> computeCandidates(CommunicationModel model, Predicate<Object> guard) {
        List<Envelope> sameModel = pending.stream().filter(e -> e.model() == model).toList();
        return switch (model) {
            case ASYN -> sameModel.stream().filter(e -> guard.test(e.value())).toList();

            case P2P -> {
                Map<Tid, Envelope> earliestPerSender = new LinkedHashMap<>();
                for (Envelope e : sameModel) {
                    if (guard.test(e.value())) {
                        earliestPerSender.putIfAbsent(e.src(), e);
                    }
                }
                yield new ArrayList<>(earliestPerSender.values());
            }

            case CD -> {
                List<Envelope> matches =
                        sameModel.stream().filter(e -> guard.test(e.value())).toList();
                List<Envelope> causallyMinimal = new ArrayList<>();
                for (Envelope e : matches) {
                    boolean dominated =
                            matches.stream().anyMatch(other -> other != e && other.clock().lessThan(e.clock()));
                    if (!dominated) {
                        causallyMinimal.add(e);
                    }
                }
                yield causallyMinimal;
            }

            case MBOX ->
                    sameModel.stream()
                            .filter(e -> guard.test(e.value()))
                            .min(Comparator.comparingLong(Envelope::seq))
                            .map(List::of)
                            .orElseGet(List::of);
        };
    }
}