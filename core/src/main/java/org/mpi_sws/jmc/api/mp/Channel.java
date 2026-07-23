package org.mpi_sws.jmc.api.mp;

import org.mpi_sws.jmc.runtime.JmcRuntimeUtils;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * The public {@code send}/{@code recv} API (Must paper &sect;1.1): {@code sendᴹ(t, v)} and the two
 * flavors of {@code recvᴹ,β(λx : e)} (blocking {@code β=b} and non-blocking {@code β=nb}).
 *
 * <p>Registers this package's per-iteration reset hooks on class load, so mailboxes and causal
 * clocks from a previous {@code @JmcCheck} iteration never leak into the next.
 */
public final class Channel {

    static {
        JmcRuntimeUtils.registerIterationResetHook(MailboxRegistry::reset);
        JmcRuntimeUtils.registerIterationResetHook(CausalClocks::resetCurrentThread);
    }

    private Channel() {}

    /** {@code sendᴹ(dst, value)}: sends from the current task to {@code dst} under {@code model}.
     *  Never blocks. */
    public static void send(CommunicationModel model, Tid dst, MpMessage value) {
        Tid src = Tid.current();
        Envelope envelope = new Envelope(
                src, dst, model, value, CausalClocks.tickAndGet(), MailboxRegistry.nextSeq());
        MailboxRegistry.get(dst).deposit(envelope);
        MpEvent.send(src, dst, model, value);
    }

    /** {@code recvᴹ()}: blocking receive of any message under {@code model}. */
    public static Object recv(CommunicationModel model) {
        return recv(model, v -> true);
    }

    /** {@code recvᴹ(λx : e)}: blocking selective receive; blocks (yielding repeatedly) until a
     *  matching message is available. */
    public static Object recv(CommunicationModel model, Predicate<Object> guard) {
        Tid self = Tid.current();
        Envelope envelope = MailboxRegistry.get(self).receiveBlocking(model, guard);
        CausalClocks.observe(envelope.clock());
        MpEvent.received(self, envelope.src(), model, envelope.value());
        return envelope.value();
    }

    /** Typed convenience: blocking receive filtered to instances of {@code type}. */
    public static <T extends MpMessage> T recv(CommunicationModel model, Class<T> type) {
        return type.cast(recv(model, type::isInstance));
    }

    /** Typed convenience: blocking selective receive of {@code type} additionally satisfying
     *  {@code guard}. */
    public static <T extends MpMessage> T recv(
            CommunicationModel model, Class<T> type, Predicate<T> guard) {
        return type.cast(recv(model, v -> type.isInstance(v) && guard.test(type.cast(v))));
    }

    /** {@code recvᴹ,ⁿᵇ()}: non-blocking receive; {@link Optional#empty()} is the {@code ⊥} case. */
    public static Optional<Object> tryRecv(CommunicationModel model) {
        return tryRecv(model, v -> true);
    }

    /** {@code recvᴹ,ⁿᵇ(λx : e)}: non-blocking selective receive; returns immediately either way. */
    public static Optional<Object> tryRecv(CommunicationModel model, Predicate<Object> guard) {
        Tid self = Tid.current();
        Optional<Envelope> envelope = MailboxRegistry.get(self).receiveNonBlocking(model, guard);
        if (envelope.isEmpty()) {
            MpEvent.receivedBottom(self, model);
            return Optional.empty();
        }
        CausalClocks.observe(envelope.get().clock());
        MpEvent.received(self, envelope.get().src(), model, envelope.get().value());
        return Optional.of(envelope.get().value());
    }

    /** Typed convenience: non-blocking receive filtered to instances of {@code type}. */
    public static <T extends MpMessage> Optional<T> tryRecv(CommunicationModel model, Class<T> type) {
        return tryRecv(model, type::isInstance).map(type::cast);
    }

    /** Typed convenience: non-blocking selective receive of {@code type} additionally satisfying
     *  {@code guard}. */
    public static <T extends MpMessage> Optional<T> tryRecv(
            CommunicationModel model, Class<T> type, Predicate<T> guard) {
        return tryRecv(model, (Predicate<Object>) v -> type.isInstance(v) && guard.test(type.cast(v)))
                .map(type::cast);
    }
}